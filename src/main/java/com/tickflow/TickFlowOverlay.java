package com.tickflow;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * RuneLite-native movable timeline overlay.
 * Past / Now / Next are separated by distinct zone colors so the timeline reads at a glance.
 */
public class TickFlowOverlay extends Overlay
{
	// Panel chrome
	private static final Color PANEL_BG = new Color(14, 16, 18, 185);
	private static final Color PANEL_BORDER = new Color(58, 62, 68);

	// PAST — muted slate (receding history)
	private static final Color PAST_FILL = new Color(38, 40, 44, 235);
	private static final Color PAST_BORDER = new Color(88, 92, 98);
	private static final Color PAST_BAR = new Color(120, 124, 130);
	private static final Color PAST_TEXT = new Color(170, 174, 178);

	// NOW — bright mint (current tick, strongest signal)
	private static final Color NOW_FILL = new Color(0, 70, 55, 200);
	private static final Color NOW_BORDER = new Color(0, 230, 170);
	private static final Color NOW_BAR = new Color(0, 240, 180);
	private static final Color NOW_TEXT = new Color(235, 255, 245);

	// NEXT idle — grey until something is queued / inferred (same family as PAST)
	private static final Color NEXT_IDLE_FILL = new Color(38, 40, 44, 235);
	private static final Color NEXT_IDLE_BORDER = new Color(88, 92, 98);
	private static final Color NEXT_IDLE_BAR = new Color(120, 124, 130);
	private static final Color NEXT_IDLE_TEXT = new Color(170, 174, 178);

	// NEXT active — cool blue when a future action / ready cue is present
	private static final Color NEXT_FILL = new Color(28, 40, 58, 235);
	private static final Color NEXT_BORDER = new Color(90, 140, 200);
	private static final Color NEXT_BAR = new Color(110, 170, 230);
	private static final Color NEXT_TEXT = new Color(180, 210, 245);

	// Stronger blue for attack-ready marker inside NEXT
	private static final Color READY_FILL = new Color(20, 55, 90, 230);
	private static final Color READY_BORDER = new Color(80, 190, 255);
	private static final Color READY_TEXT = new Color(200, 235, 255);

	private static final Color CYCLE_TRACK = new Color(32, 36, 40, 230);
	private static final Color CYCLE_RING = new Color(70, 78, 86);
	private static final Color CYCLE_PROGRESS = new Color(0, 220, 165);
	private static final Color CYCLE_READY_GLOW = new Color(0, 240, 180, 90);
	private static final Color CYCLE_UNKNOWN = new Color(110, 114, 118);
	private static final Color FEEDBACK_BADGE_OK = new Color(24, 70, 48, 230);
	private static final Color FEEDBACK_BADGE_LATE = new Color(78, 48, 28, 230);
	private static final Color TITLE = new Color(235, 235, 235);
	private static final Color MUTED = new Color(150, 154, 158);
	private static final Color VOLUME_BAR_OFF = new Color(150, 154, 158, 90);
	private static final Color FEEDBACK_OK = new Color(140, 220, 175);
	private static final Color FEEDBACK_LATE = new Color(230, 165, 150);
	private static final Color PULSE_TRACK = new Color(40, 44, 48);
	private static final Color PULSE_FILL = new Color(0, 230, 170);
	private static final Color PULSE_MARKER = new Color(255, 255, 255);
	private static final Color PULSE_LATE_ZONE = new Color(48, 44, 36);

	private static final ZoneStyle STYLE_PAST = new ZoneStyle(PAST_FILL, PAST_BORDER, PAST_BAR, PAST_TEXT, PAST_TEXT);
	private static final ZoneStyle STYLE_NOW = new ZoneStyle(NOW_FILL, NOW_BORDER, NOW_BAR, NOW_TEXT, NOW_BAR);
	private static final ZoneStyle STYLE_NEXT_IDLE = new ZoneStyle(
		NEXT_IDLE_FILL, NEXT_IDLE_BORDER, NEXT_IDLE_BAR, NEXT_IDLE_TEXT, NEXT_IDLE_TEXT);
	private static final ZoneStyle STYLE_NEXT = new ZoneStyle(NEXT_FILL, NEXT_BORDER, NEXT_BAR, NEXT_TEXT, NEXT_TEXT);
	private static final ZoneStyle STYLE_READY = new ZoneStyle(READY_FILL, READY_BORDER, READY_BORDER, READY_TEXT, READY_TEXT);

	private static final AlphaComposite CYCLE_ICON_DIM = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f);
	private static final AlphaComposite CYCLE_ICON_READY = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f);

	private static final String[] RELATIVE_LABELS = {
		"-4", "-3", "-2", "-1", "0", "+1", "+2", "+3", "+4"
	};

	private final TickFlowPlugin plugin;
	private final TickFlowConfig config;
	private final MouseManager mouseManager;
	private final Arc2D.Double scratchArc = new Arc2D.Double();
	private final RoundRectangle2D.Float scratchFrame = new RoundRectangle2D.Float();
	private static final BasicStroke STYLE_BUTTON_STROKE =
		new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private float cachedCycleStrokeWidth = -1f;
	@Nullable
	private Stroke cachedCycleStroke;

	@Nullable
	private TickFlowState.Snapshot cachedSnap;
	@Nullable
	private List<SlotView> cachedSlots;
	private int cachedSlotCount = -1;
	@Nullable
	private Dimension cachedDimension;
	private int cachedWidth = -1;
	private int cachedHeight = -1;
	private final Rectangle muteButtonBounds = new Rectangle();
	private final Rectangle styleButtonBounds = new Rectangle();
	private final Rectangle volumeButtonBounds = new Rectangle();
	private final MouseAdapter headerClickListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (event.getButton() != MouseEvent.BUTTON1)
			{
				return event;
			}
			final Point mouse = event.getPoint();
			final Rectangle overlayBounds = getBounds();
			if (overlayBounds == null)
			{
				return event;
			}
			final Point local = new Point(mouse.x - overlayBounds.x, mouse.y - overlayBounds.y);
			if (!styleButtonBounds.isEmpty() && styleButtonBounds.contains(local))
			{
				plugin.toggleTimelineStyle();
				return null;
			}
			if (!muteButtonBounds.isEmpty() && muteButtonBounds.contains(local))
			{
				plugin.toggleTickSoundMute();
				return null;
			}
			if (!volumeButtonBounds.isEmpty() && volumeButtonBounds.contains(local))
			{
				plugin.cycleTickSoundVolume();
				return null;
			}
			return event;
		}
	};

	@Inject
	private TickFlowOverlay(TickFlowPlugin plugin, TickFlowConfig config, MouseManager mouseManager)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.mouseManager = mouseManager;
		setPosition(OverlayPosition.BOTTOM_LEFT);
		setPriority(PRIORITY_MED);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "TickFlow overlay");
	}

	void registerInput()
	{
		mouseManager.registerMouseListener(headerClickListener);
	}

	void unregisterInput()
	{
		mouseManager.unregisterMouseListener(headerClickListener);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enabledOverlay() || config.timelineStyle() != TimelineStyle.SQUARE)
		{
			muteButtonBounds.setBounds(0, 0, 0, 0);
			styleButtonBounds.setBounds(0, 0, 0, 0);
			volumeButtonBounds.setBounds(0, 0, 0, 0);
			return null;
		}

		TickFlowState.Snapshot snap = plugin.getSnapshot();
		if (snap == null || snap.getLocalTickIndex() < 0)
		{
			muteButtonBounds.setBounds(0, 0, 0, 0);
			styleButtonBounds.setBounds(0, 0, 0, 0);
			volumeButtonBounds.setBounds(0, 0, 0, 0);
			return null;
		}
		if (config.autoHideOutsideCombat() && !snap.isInCombat())
		{
			muteButtonBounds.setBounds(0, 0, 0, 0);
			styleButtonBounds.setBounds(0, 0, 0, 0);
			volumeButtonBounds.setBounds(0, 0, 0, 0);
			return null;
		}

		int scalePct = snapScale(config.overlayScale());
		int slotCount = TickFlowState.clampTimeline(config.timelineLength());
		boolean minimal = config.mode() == OverlayMode.MINIMAL;
		boolean compact = config.mode() == OverlayMode.COMPACT || minimal;

		int slotW = scale(TickFlowLayout.BASE_CELL, scalePct);
		int slotH = scale(TickFlowLayout.BASE_CELL, scalePct);
		int nowSlotW = TickFlowLayout.nowSize(slotW);
		int nowSlotH = TickFlowLayout.nowSize(slotH);
		int gap = scale(TickFlowLayout.BASE_GAP, scalePct);
		int pad = scale(TickFlowLayout.BASE_PAD, scalePct);
		int barH = scale(3, scalePct);
		int pulseH = 0;
		int pulseGap = 0;
		int headerH = scale(12, scalePct);
		int sectionH = compact ? 0 : scale(16, scalePct);
		CycleFeedback feedback = (!minimal && config.showCycleFeedback()) ? snap.getCycleFeedback() : null;
		boolean showCycleHud = !minimal && (config.showReadiness() || feedback != null);
		int cycleHudH = showCycleHud ? scale(36, scalePct) : 0;
		int cycleHudGap = showCycleHud ? scale(3, scalePct) : 0;

		int debugH = (!minimal && config.debugMode()) ? scale(78, scalePct) : 0;

		int btnSize = scale(12, scalePct);
		int btnGap = scale(6, scalePct);
		boolean soundOn = plugin.isTickSoundEnabled();
		int controlCount = soundOn ? 3 : 2;
		int controlsW = controlCount * btnSize + (controlCount - 1) * btnGap;

		int contentW = minimal
			? Math.max(nowSlotW, controlsW)
			: (slotCount - 1) * (slotW + gap) + nowSlotW;
		int width = contentW + pad * 2;
		int height = pad + headerH + gap + nowSlotH + pulseGap + pulseH + sectionH
			+ cycleHudGap + cycleHudH + debugH + pad;

		configureCrispGraphics(graphics);
		Object oldAa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		Font bodyFont = FontManager.getRunescapeFont();
		Font smallFont = FontManager.getRunescapeSmallFont();

		int corner = scale(10, scalePct);
		scratchFrame.setRoundRect(0, 0, width - 1, height - 1, corner, corner);
		graphics.setColor(PANEL_BG);
		graphics.fill(scratchFrame);
		graphics.setColor(PANEL_BORDER);
		graphics.draw(scratchFrame);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
		configureCrispGraphics(graphics);

		int y = pad;
		int muteX = width - pad - btnSize;
		int cursorX = muteX;
		int btnY = y + Math.max(0, (headerH - btnSize) / 2);
		muteButtonBounds.setBounds(muteX - 2, btnY - 2, btnSize + 4, btnSize + 4);
		drawMuteButton(graphics, muteX, btnY, btnSize, plugin.isTickSoundAudible());
		if (soundOn)
		{
			cursorX = muteX - btnGap - btnSize;
			volumeButtonBounds.setBounds(cursorX - 2, btnY - 2, btnSize + 4, btnSize + 4);
			drawVolumeButton(graphics, cursorX, btnY, btnSize, plugin.getTickSoundVolume(), plugin.isTickSoundAudible());
		}
		else
		{
			volumeButtonBounds.setBounds(0, 0, 0, 0);
		}
		int styleX = cursorX - btnGap - btnSize;
		styleButtonBounds.setBounds(styleX - 2, btnY - 2, btnSize + 4, btnSize + 4);
		drawStyleButton(graphics, styleX, btnY, btnSize, TimelineStyle.SQUARE);

		y += headerH + gap;

		List<SlotView> slots = slotsFor(snap, slotCount);
		double tickProgress = plugin.getTickProgress();
		boolean showMiniPulse = config.showTickPulse();
		int rowH = nowSlotH;
		int xCursor = pad + (minimal ? Math.max(0, (contentW - nowSlotW) / 2) : 0);
		int[] slotXs = new int[slots.size()];
		int[] slotWs = new int[slots.size()];
		int nowIndex = -1;

		for (int i = 0; i < slots.size(); i++)
		{
			SlotView slot = slots.get(i);
			Zone zone = zoneFor(slot);
			boolean now = zone == Zone.NOW;
			if (minimal && !now)
			{
				continue;
			}
			int cellW = now ? nowSlotW : slotW;
			int cellH = now ? nowSlotH : slotH;
			int x = xCursor;
			int cellY = y + (rowH - cellH) / 2;
			slotXs[i] = x;
			slotWs[i] = cellW;
			if (now)
			{
				nowIndex = i;
			}
			ZoneStyle style = styleFor(zone, slot);

			graphics.setColor(style.fill);
			graphics.fillRect(x, cellY, cellW, cellH);

			graphics.setColor(style.bar);
			graphics.fillRect(x, cellY, cellW, barH);

			graphics.setColor(style.border);
			graphics.drawRect(x, cellY, cellW - 1, cellH - 1);
			if (now)
			{
				graphics.drawRect(x + 1, cellY + 1, cellW - 3, cellH - 3);
			}

			if (!minimal)
			{
				graphics.setFont(smallFont);
				graphics.setColor(style.meta);
				String idx = formatRelative(slot.relative);
				FontMetrics idxFm = graphics.getFontMetrics();
				graphics.drawString(idx, x + (cellW - idxFm.stringWidth(idx)) / 2, cellY + barH + ascent(graphics) + 1);
			}

			drawActionIcon(graphics, slot, x, cellY + barH, cellW, cellH - barH);

			if (now && showMiniPulse)
			{
				int innerPad = 3;
				int trackY = cellY + cellH - innerPad - 3;
				int trackW = cellW - innerPad * 2;
				drawPulseFill(graphics, x + innerPad, trackY, trackW, 3, tickProgress, false, 0, true);
			}

			xCursor += cellW + gap;
		}

		y += rowH;

		if (!compact)
		{
			graphics.setFont(smallFont);
			drawZoneHeaders(graphics, slots, slotXs, slotWs, nowIndex, y);
			y += sectionH;
		}

		if (showCycleHud)
		{
			y += cycleHudGap;
			drawCycleHud(graphics, snap, feedback, pad, y, contentW, cycleHudH, tickProgress, bodyFont, smallFont);
			y += cycleHudH;
		}

		if (!minimal && config.debugMode())
		{
			graphics.setFont(smallFont);
			graphics.setColor(MUTED);
			int dy = y + ascent(graphics);
			int line = graphics.getFontMetrics().getHeight();
			graphics.drawString("tick=" + snap.getLocalTickIndex() + " anim=" + snap.getAnimationId(), pad, dy);
			dy += line;
			graphics.drawString("target=" + nullSafe(snap.getTargetIdentity()), pad, dy);
			dy += line;
			graphics.drawString("pos=" + nullSafe(snap.getPlayerLocation()), pad, dy);
			dy += line;
			graphics.drawString("equip=" + nullSafe(snap.getEquipmentFingerprint()), pad, dy);
			dy += line;
			graphics.drawString(snap.getTrackerDebug(), pad, dy);
			dy += line;
			if (!snap.getBufferedMenus().isEmpty())
			{
				graphics.drawString("menus=" + snap.getBufferedMenus(), pad, dy);
			}
		}

		if (cachedDimension == null || cachedWidth != width || cachedHeight != height)
		{
			cachedWidth = width;
			cachedHeight = height;
			cachedDimension = new Dimension(width, height);
		}
		return cachedDimension;
	}

	private void drawStyleButton(Graphics2D g, int x, int y, int size, TimelineStyle current)
	{
		Object oldAa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Stroke old = g.getStroke();
		g.setStroke(STYLE_BUTTON_STROKE);
		g.setColor(MUTED);
		int inset = 1;
		int s = size - inset * 2 - 1;
		// Icon is the mode you switch into (circle → circular, square → square).
		if (current == TimelineStyle.CIRCULAR)
		{
			g.drawRoundRect(x + inset, y + inset, s, s, 2, 2);
		}
		else
		{
			g.drawOval(x + inset, y + inset, s, s);
		}
		g.setStroke(old);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
	}

	private void drawMuteButton(Graphics2D g, int x, int y, int size, boolean audible)
	{
		g.setColor(audible ? NOW_BAR : MUTED);
		// Speaker body
		int bodyW = Math.max(2, size / 3);
		int bodyH = Math.max(4, size * 2 / 3);
		int bodyX = x;
		int bodyY = y + (size - bodyH) / 2;
		g.fillRect(bodyX, bodyY + bodyH / 4, bodyW, bodyH / 2);
		// Cone
		int[] xs = {bodyX + bodyW - 1, x + size - 2, x + size - 2, bodyX + bodyW - 1};
		int[] ys = {bodyY + bodyH / 4, bodyY, bodyY + bodyH, bodyY + bodyH - bodyH / 4};
		g.fillPolygon(xs, ys, 4);

		if (!audible)
		{
			g.setColor(MUTED);
			g.drawLine(x, y, x + size - 1, y + size - 1);
		}
		else
		{
			// Soft sound arcs
			g.drawArc(x + size / 2, y + 1, size / 2, size - 2, -35, 70);
		}
	}

	/** Compact 3-bar volume control — click cycles quiet presets. */
	private void drawVolumeButton(Graphics2D g, int x, int y, int size, int volumePct, boolean audible)
	{
		int bars = 3;
		int gap = 1;
		int barW = Math.max(2, (size - gap * (bars - 1)) / bars);
		int lit = volumePct < 50 ? 1 : volumePct < 70 ? 2 : 3;
		Color on = audible ? NOW_BAR : MUTED;
		for (int i = 0; i < bars; i++)
		{
			int barH = Math.max(3, (size * (i + 1)) / bars);
			int bx = x + i * (barW + gap);
			int by = y + size - barH;
			g.setColor(i < lit ? on : VOLUME_BAR_OFF);
			g.fillRect(bx, by, barW, barH);
		}
	}

	private List<SlotView> slotsFor(TickFlowState.Snapshot snap, int slotCount)
	{
		if (cachedSlots != null && cachedSnap == snap && cachedSlotCount == slotCount)
		{
			return cachedSlots;
		}
		cachedSnap = snap;
		cachedSlotCount = slotCount;
		cachedSlots = buildSlots(snap, slotCount);
		return cachedSlots;
	}

	private void drawTickPulse(
		Graphics2D g,
		int x,
		int y,
		int width,
		int height,
		double progress,
		int nowIndex,
		int slotW,
		int gap,
		double lateTickStart,
		boolean lateCue)
	{
		int trackH = Math.max(4, height - 2);
		int trackY = y + (height - trackH) / 2;
		int innerX = x + 1;
		int innerW = width - 2;
		int innerY = trackY + 1;
		int innerH = trackH - 2;
		double threshold = clamp01(lateTickStart);

		g.setColor(PULSE_TRACK);
		g.fillRect(x, trackY, width, trackH);

		if (lateCue)
		{
			int zoneX = innerX + (int) Math.round(innerW * threshold);
			int zoneW = Math.max(0, innerX + innerW - zoneX);
			g.setColor(PULSE_LATE_ZONE);
			g.fillRect(zoneX, innerY, zoneW, innerH);
		}

		g.setColor(PANEL_BORDER);
		g.drawRect(x, trackY, width - 1, trackH - 1);

		// Soft yellow → green mapped across the full bar; fill reveals it as the tick progresses.
		double p = clamp01(progress);
		int fillW = (int) Math.round(innerW * p);
		if (fillW > 0 && innerW > 0)
		{
			Paint oldPaint = g.getPaint();
			g.setPaint(new GradientPaint(
				innerX, innerY, TickFlowLayout.PROGRESS_START,
				innerX + innerW, innerY, TickFlowLayout.PROGRESS_END));
			g.fillRect(innerX, innerY, fillW, innerH);
			g.setPaint(oldPaint);
		}

		int markerX = innerX + Math.max(0, fillW - 1);
		g.setColor(PULSE_MARKER);
		g.fillRect(markerX, trackY - 1, 2, trackH + 2);

		if (nowIndex >= 0)
		{
			int nowCenter = x + nowIndex * (slotW + gap) + slotW / 2;
			g.setColor(NOW_BAR);
			g.fillRect(nowCenter - 1, trackY + trackH, 2, 2);
		}
	}

	/**
	 * @param paintTrack if true, clears/paints the track first (mini in-slot bar).
	 *                   Main pulse already painted its track before calling this.
	 */
	/**
	 * Mini in-slot pulse — soft yellow → green through the tick so resets read clearly.
	 */
	private static void drawPulseFill(
		Graphics2D g,
		int x,
		int y,
		int width,
		int height,
		double progress,
		boolean lateCue,
		double lateTickStart,
		boolean paintTrack)
	{
		if (paintTrack)
		{
			g.setColor(PULSE_TRACK);
			g.fillRect(x, y, width, height);
		}

		double p = clamp01(progress);
		int fillW = (int) Math.round(width * p);
		if (fillW <= 0 || width <= 0)
		{
			return;
		}

		Color tip = TickFlowLayout.progressColor(p);
		g.setColor(tip);
		g.fillRect(x, y, fillW, height);
		g.fillRect(x + Math.max(0, fillW - 2), y - 1, 2, height + 2);
	}

	/**
	 * Fraction of the pulse bar where the NEXT column begins (NOW/NEXT boundary).
	 */
	private static double lateTickStartFraction(int nowIndex, int slotCount, int slotW, int gap, int contentW)
	{
		if (nowIndex < 0 || contentW <= 0 || slotCount <= 0)
		{
			return 0.60;
		}
		int nextStart = nowIndex + 1;
		if (nextStart >= slotCount)
		{
			return 0.75;
		}
		int offset = nextStart * (slotW + gap);
		return clamp01(offset / (double) contentW);
	}

	private static double clamp01(double value)
	{
		if (value < 0)
		{
			return 0;
		}
		if (value > 1)
		{
			return 1;
		}
		return value;
	}

	private static boolean isQueuedNext(SlotView slot)
	{
		return slot.readyMarker || !slot.type.isEmpty();
	}

	private void drawZoneHeaders(Graphics2D g, List<SlotView> slots, int[] slotXs, int[] slotWs, int nowIndex, int y)
	{
		int textY = y + ascent(g) + 4;

		if (nowIndex > 0)
		{
			int x0 = slotXs[0];
			int x1 = slotXs[nowIndex - 1] + slotWs[nowIndex - 1];
			drawZoneHeader(g, "PAST", PAST_TEXT, x0, x1, textY, Align.LEFT);
		}

		if (nowIndex >= 0)
		{
			int x0 = slotXs[nowIndex];
			int x1 = x0 + slotWs[nowIndex];
			drawZoneHeader(g, "NOW", NOW_TEXT, x0, x1, textY, Align.CENTER);
		}

		if (nowIndex >= 0 && nowIndex < slots.size() - 1)
		{
			int x0 = slotXs[nowIndex + 1];
			int x1 = slotXs[slots.size() - 1] + slotWs[slots.size() - 1];
			boolean nextActive = hasQueuedNext(slots, nowIndex);
			drawZoneHeader(
				g,
				"NEXT",
				nextActive ? NEXT_TEXT : NEXT_IDLE_TEXT,
				x0,
				x1,
				textY,
				Align.RIGHT);
		}
	}

	private static boolean hasQueuedNext(List<SlotView> slots, int nowIndex)
	{
		for (int i = nowIndex + 1; i < slots.size(); i++)
		{
			if (isQueuedNext(slots.get(i)))
			{
				return true;
			}
		}
		return false;
	}

	private static void drawZoneHeader(Graphics2D g, String label, Color text, int x0, int x1, int textY, Align align)
	{
		g.setColor(text);
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(label);
		int labelX;
		switch (align)
		{
			case LEFT:
				labelX = x0;
				break;
			case RIGHT:
				labelX = x1 - w;
				break;
			default:
				labelX = x0 + Math.max(0, ((x1 - x0) - w) / 2);
				break;
		}
		g.drawString(label, labelX, textY);
	}

	private enum Align
	{
		LEFT,
		CENTER,
		RIGHT
	}

	/**
	 * WoW-style circular attack-cycle readout: clockwise remaining sweep, center tick count,
	 * optional compact feedback badge. Replaces dense footer text for fast combat glances.
	 */
	private void drawCycleHud(
		Graphics2D g,
		TickFlowState.Snapshot snap,
		@Nullable CycleFeedback feedback,
		int x,
		int y,
		int width,
		int height,
		double tickProgress,
		Font bodyFont,
		Font smallFont)
	{
		Object oldAa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int diameter = Math.max(24, height - 2);
		int cy = y + height / 2;
		int cursor = x;

		if (config.showReadiness())
		{
			int cx = cursor + diameter / 2;
			boolean known = snap.getConfidence().allowsReadiness() && snap.getCandidateSpeed() > 0;
			if (known)
			{
				int speed = Math.max(1, snap.getCandidateSpeed());
				boolean ready = snap.isAttackReadyNow() || snap.getTicksUntilReady() == 0;
				int until = Math.max(0, snap.getTicksUntilReady());
				double remaining = ready ? 0 : Math.max(0, until - clamp01(tickProgress));
				double remainingFrac = Math.min(1.0, remaining / speed);
				drawCooldownCircle(g, cx, cy, diameter, remainingFrac, ready, ready ? -1 : until, bodyFont);
			}
			else
			{
				drawCooldownCircle(g, cx, cy, diameter, 0, false, -2, bodyFont);
			}
			cursor += diameter + 8;
		}

		if (feedback != null)
		{
			int badgeD = Math.max(18, diameter * 2 / 3);
			int badgeCx = cursor + badgeD / 2;
			if (badgeCx + badgeD / 2 > x + width)
			{
				badgeCx = x + width - badgeD / 2;
			}
			drawFeedbackBadge(g, badgeCx, cy, badgeD, feedback, smallFont);
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
	}

	/**
	 * @param centerCode {@code >=0} ticks remaining, {@code -1} ready, {@code -2} unknown
	 */
	private void drawCooldownCircle(
		Graphics2D g,
		int cx,
		int cy,
		int diameter,
		double remainingFrac,
		boolean ready,
		int centerCode,
		Font font)
	{
		int r = diameter / 2;
		int x = cx - r;
		int y = cy - r;
		double elapsed = 1.0 - clamp01(remainingFrac);

		g.setColor(CYCLE_TRACK);
		g.fillOval(x, y, diameter, diameter);

		BufferedImage icon = plugin.getIcons().getAttack(plugin.getCombatStyle());
		if (icon != null)
		{
			// Draw 1:1 at native crisp size — never stretch into the circle.
			int iw = icon.getWidth();
			int ih = icon.getHeight();
			Composite old = g.getComposite();
			g.setComposite(ready ? CYCLE_ICON_READY : CYCLE_ICON_DIM);
			g.drawImage(icon, cx - iw / 2, cy - ih / 2, null);
			g.setComposite(old);
		}

		if (ready)
		{
			g.setColor(CYCLE_READY_GLOW);
			g.fillOval(x + 2, y + 2, diameter - 4, diameter - 4);
		}

		Stroke oldStroke = g.getStroke();
		g.setStroke(cycleStroke(diameter));
		g.setColor(CYCLE_RING);
		g.drawOval(x + 1, y + 1, diameter - 2, diameter - 2);

		if (ready)
		{
			g.setColor(CYCLE_PROGRESS);
			g.drawOval(x + 1, y + 1, diameter - 2, diameter - 2);
		}
		else if (elapsed > 0.001)
		{
			// Single green arc fills clockwise from 12 o'clock as the cycle progresses.
			g.setColor(CYCLE_PROGRESS);
			scratchArc.setArc(x + 1, y + 1, diameter - 2, diameter - 2, 90, -elapsed * 360.0, Arc2D.OPEN);
			g.draw(scratchArc);
		}
		else if (centerCode == -2)
		{
			g.setColor(CYCLE_UNKNOWN);
			g.drawOval(x + 1, y + 1, diameter - 2, diameter - 2);
		}
		g.setStroke(oldStroke);

		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		String text;
		Color textColor;
		if (centerCode == -1)
		{
			text = "0";
			textColor = CYCLE_PROGRESS;
		}
		else if (centerCode == -2)
		{
			text = "-";
			textColor = MUTED;
		}
		else
		{
			text = Integer.toString(centerCode);
			textColor = TITLE;
		}
		g.setColor(textColor);
		g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
	}

	private static void drawFeedbackBadge(Graphics2D g, int cx, int cy, int diameter, CycleFeedback feedback, Font font)
	{
		int r = diameter / 2;
		boolean ok = feedback.getDeltaTicks() == 0;
		g.setColor(ok ? FEEDBACK_BADGE_OK : FEEDBACK_BADGE_LATE);
		g.fillOval(cx - r, cy - r, diameter, diameter);
		g.setColor(ok ? FEEDBACK_OK : FEEDBACK_LATE);
		g.drawOval(cx - r, cy - r, diameter - 1, diameter - 1);

		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		String text = ok ? "OK" : "+" + feedback.getDeltaTicks();
		g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
	}

	private static void configureCrispGraphics(Graphics2D graphics)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
	}

	private List<SlotView> buildSlots(TickFlowState.Snapshot snap, int slotCount)
	{
		int before = slotCount / 2;
		int after = slotCount - before - 1;

		List<TickRecord> history = snap.getHistory();
		TickRecord current = snap.getCurrentTick();

		List<SlotView> slots = new ArrayList<>(slotCount);
		for (int rel = -before; rel <= after; rel++)
		{
			if (rel < 0)
			{
				int histIndex = history.size() + rel;
				if (histIndex >= 0 && histIndex < history.size())
				{
					slots.add(SlotView.fromRecord(rel, history.get(histIndex)));
				}
				else
				{
					slots.add(SlotView.empty(rel));
				}
			}
			else if (rel == 0)
			{
				if (current != null)
				{
					slots.add(SlotView.fromRecord(0, current));
				}
				else if (!history.isEmpty())
				{
					slots.add(SlotView.fromRecord(0, history.get(history.size() - 1)));
				}
				else
				{
					slots.add(SlotView.empty(0));
				}
			}
			else
			{
				boolean readyMarker = config.showReadiness()
					&& snap.getConfidence().allowsReadiness()
					&& snap.getTicksUntilReady() == rel;
				String label = readyMarker ? "Ready" : "";
				ActionType type = readyMarker ? ActionType.ATTACK : ActionType.EMPTY;
				slots.add(new SlotView(rel, type, label, readyMarker, TickAction.NO_SPRITE));
			}
		}
		return slots;
	}

	private void drawActionIcon(Graphics2D g, SlotView slot, int x, int y, int slotW, int slotH)
	{
		ActionType type = slot.readyMarker ? ActionType.ATTACK : slot.type;
		BufferedImage icon;
		if (type == ActionType.ATTACK || slot.readyMarker)
		{
			icon = plugin.getIcons().getAttack(plugin.getCombatStyle());
		}
		else if (slot.spriteId >= 0)
		{
			icon = plugin.getIcons().getSprite(slot.spriteId);
			// Specific prayer sprite still loading — keep neutral orb, never a wrong prayer icon.
			if (icon == null && type == ActionType.PRAYER)
			{
				icon = plugin.getIcons().get(ActionType.PRAYER);
			}
		}
		else
		{
			icon = plugin.getIcons().get(type);
		}

		if (icon != null)
		{
			// Integer pixel blit at native size — avoid dest-width/height stretches.
			int ix = x + (slotW - icon.getWidth()) / 2;
			int iy = y + (slotH - icon.getHeight()) / 2;
			g.drawImage(icon, ix, iy, null);
		}
		else if (type != ActionType.EMPTY)
		{
			// Sprites may still be loading after login — brief text fallback.
			g.setFont(FontManager.getRunescapeBoldFont());
			g.setColor(NOW_TEXT);
			String glyph = glyphFor(type);
			FontMetrics fm = g.getFontMetrics();
			int gx = x + (slotW - fm.stringWidth(glyph)) / 2;
			int gy = y + (slotH + fm.getAscent() - fm.getDescent()) / 2 + 1;
			g.drawString(glyph, gx, gy);
		}

		if (!slot.secondary.isEmpty())
		{
			SecondaryIcon secondary = slot.secondary.get(0);
			BufferedImage secondaryIcon = secondary.spriteId >= 0
				? plugin.getIcons().getSpriteSmall(secondary.spriteId)
				: plugin.getIcons().getSmall(secondary.type);
			if (secondaryIcon != null)
			{
				int sx = x + slotW - secondaryIcon.getWidth() - 2;
				int sy = y + 2;
				g.drawImage(secondaryIcon, sx, sy, null);
			}
			else
			{
				g.setColor(PAST_BAR);
				g.fillRect(x + slotW - 6, y + 3, 3, 3);
			}
		}
	}

	private Stroke cycleStroke(int diameter)
	{
		float strokeW = Math.max(2.5f, diameter / 10f);
		if (cachedCycleStroke == null || Float.compare(cachedCycleStrokeWidth, strokeW) != 0)
		{
			cachedCycleStrokeWidth = strokeW;
			cachedCycleStroke = new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		}
		return cachedCycleStroke;
	}

	private static Zone zoneFor(SlotView slot)
	{
		if (slot.relative < 0)
		{
			return Zone.PAST;
		}
		if (slot.relative > 0)
		{
			return Zone.NEXT;
		}
		return Zone.NOW;
	}

	private static ZoneStyle styleFor(Zone zone, SlotView slot)
	{
		switch (zone)
		{
			case PAST:
				return STYLE_PAST;
			case NOW:
				return STYLE_NOW;
			case NEXT:
			default:
				if (slot.readyMarker)
				{
					return STYLE_READY;
				}
				if (isQueuedNext(slot))
				{
					return STYLE_NEXT;
				}
				return STYLE_NEXT_IDLE;
		}
	}

	private static String formatRelative(int relative)
	{
		int index = relative + 4;
		if (index >= 0 && index < RELATIVE_LABELS.length)
		{
			return RELATIVE_LABELS[index];
		}
		return relative > 0 ? "+" + relative : Integer.toString(relative);
	}

	private static String glyphFor(ActionType type)
	{
		switch (type)
		{
			case ATTACK:
				return "Atk";
			case MOVE:
				return "Mov";
			case PRAYER:
				return "Pry";
			case CONSUMABLE:
				return "Itm";
			case OTHER:
				return "Oth";
			case EMPTY:
			default:
				return "-";
		}
	}

	private static int snapScale(int scalePct)
	{
		if (scalePct < 90)
		{
			return 80;
		}
		if (scalePct < 110)
		{
			return 100;
		}
		if (scalePct < 130)
		{
			return 120;
		}
		return 140;
	}

	private static int scale(int base, int scalePct)
	{
		return Math.max(1, (base * scalePct + 50) / 100);
	}

	private static int ascent(Graphics2D graphics)
	{
		return graphics.getFontMetrics().getAscent();
	}

	private static String nullSafe(String value)
	{
		return value == null ? "-" : value;
	}

	private enum Zone
	{
		PAST,
		NOW,
		NEXT
	}

	private static final class ZoneStyle
	{
		private final Color fill;
		private final Color border;
		private final Color bar;
		private final Color text;
		private final Color meta;

		private ZoneStyle(Color fill, Color border, Color bar, Color text, Color meta)
		{
			this.fill = fill;
			this.border = border;
			this.bar = bar;
			this.text = text;
			this.meta = meta;
		}
	}

	private static final class SecondaryIcon
	{
		private final ActionType type;
		private final int spriteId;

		private SecondaryIcon(ActionType type, int spriteId)
		{
			this.type = type;
			this.spriteId = spriteId;
		}
	}

	private static final class SlotView
	{
		private final int relative;
		private final ActionType type;
		private final String label;
		private final boolean readyMarker;
		private final int spriteId;
		private final List<SecondaryIcon> secondary = new ArrayList<>();

		private SlotView(int relative, ActionType type, String label, boolean readyMarker, int spriteId)
		{
			this.relative = relative;
			this.type = type;
			this.label = label;
			this.readyMarker = readyMarker;
			this.spriteId = spriteId;
		}

		static SlotView empty(int relative)
		{
			return new SlotView(relative, ActionType.EMPTY, ActionType.EMPTY.getShortLabel(), false, TickAction.NO_SPRITE);
		}

		static SlotView fromRecord(int relative, TickRecord record)
		{
			TickAction primary = record.getPrimaryAction();
			boolean readyOnly = record.isAttackReady() && primary.getType() == ActionType.EMPTY;
			SlotView view = new SlotView(
				relative,
				readyOnly ? ActionType.ATTACK : primary.getType(),
				readyOnly ? "Ready" : primary.getLabel(),
				readyOnly,
				primary.getSpriteId());
			for (TickAction secondaryAction : record.getSecondaryActions())
			{
				view.secondary.add(new SecondaryIcon(secondaryAction.getType(), secondaryAction.getSpriteId()));
			}
			return view;
		}
	}
}
