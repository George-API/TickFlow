package com.tickflow;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
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
 * Default TickFlow timeline: circular cells with a clockwise NOW progress ring.
 */
public class TickFlowCircleOverlay extends Overlay
{
	private static final Color PANEL_BG = new Color(14, 16, 18, 220);
	private static final Color PANEL_BORDER = new Color(58, 62, 68);
	private static final Color TITLE = new Color(235, 235, 235);
	private static final Color MUTED = new Color(150, 154, 158);
	private static final Color FEEDBACK_LATE = new Color(230, 165, 150);

	private static final Color PAST_FILL = new Color(38, 40, 44, 235);
	private static final Color PAST_RING = new Color(100, 104, 110);
	private static final Color PAST_BAR = new Color(120, 124, 130);
	private static final Color PAST_TEXT = new Color(170, 174, 178);

	private static final Color NOW_FILL = new Color(0, 70, 55, 210);
	private static final Color NOW_RING = new Color(40, 80, 70);
	private static final Color NOW_PROGRESS = new Color(0, 230, 170);
	/** Dark elapsed wedge — WoW cooldown swipe over the NOW fill/icon. */
	private static final Color NOW_SWIPE = new Color(0, 0, 0, 155);
	/** Soft hand / spark so the tip doesn't overpower the outer ring. */
	private static final Color NOW_HAND = new Color(200, 230, 210, 130);
	private static final Color NOW_SPARK = new Color(220, 245, 230, 140);
	private static final Color NOW_SPARK_CORE = new Color(255, 255, 255, 160);
	private static final Color NOW_BAR = new Color(0, 240, 180);
	private static final Color NOW_TEXT = new Color(235, 255, 245);

	private static final Color NEXT_IDLE_FILL = new Color(38, 40, 44, 235);
	private static final Color NEXT_IDLE_RING = new Color(100, 104, 110);
	private static final Color NEXT_IDLE_BAR = new Color(120, 124, 130);
	private static final Color NEXT_IDLE_TEXT = new Color(170, 174, 178);

	private static final Color NEXT_FILL = new Color(28, 40, 58, 235);
	private static final Color NEXT_RING = new Color(90, 140, 200);
	private static final Color NEXT_BAR = new Color(110, 170, 230);
	private static final Color NEXT_TEXT = new Color(180, 210, 245);

	private static final Color READY_FILL = new Color(20, 55, 90, 230);
	private static final Color READY_RING = new Color(80, 190, 255);

	private static final Color CYCLE_TRACK = new Color(32, 36, 40, 230);
	private static final Color CYCLE_RING = new Color(70, 78, 86);
	private static final Color CYCLE_PROGRESS = new Color(0, 220, 165);
	private static final Color CYCLE_READY_GLOW = new Color(0, 240, 180, 90);
	private static final Color CYCLE_UNKNOWN = new Color(110, 114, 118);
	private static final Color FEEDBACK_OK = new Color(140, 220, 175);
	private static final Color FEEDBACK_BADGE_OK = new Color(24, 70, 48, 230);
	private static final Color FEEDBACK_BADGE_LATE = new Color(78, 48, 28, 230);
	private static final AlphaComposite CYCLE_ICON_DIM = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f);
	private static final AlphaComposite CYCLE_ICON_READY = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f);
	private static final Color VOLUME_BAR_OFF = new Color(150, 154, 158, 90);
	private static final Color NOW_REMAIN_WASH = new Color(0, 220, 165, 45);
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
	private final Rectangle muteButtonBounds = new Rectangle();
	private final Rectangle styleButtonBounds = new Rectangle();
	private final Rectangle volumeButtonBounds = new Rectangle();
	private float cachedStrokeW = -1f;
	private Stroke cachedStroke;
	private float cachedNowStrokeW = -1f;
	private Stroke cachedNowStroke;
	private float cachedHandStrokeW = -1f;
	private Stroke cachedHandStroke;
	private float cachedCycleStrokeW = -1f;
	private Stroke cachedCycleStroke;
	@Nullable
	private TickFlowState.Snapshot cachedSnap;
	@Nullable
	private List<CircleSlot> cachedSlots;
	private int cachedSlotCount = -1;
	@Nullable
	private Dimension cachedDimension;
	private int cachedWidth = -1;
	private int cachedHeight = -1;

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
	private TickFlowCircleOverlay(TickFlowPlugin plugin, TickFlowConfig config, MouseManager mouseManager)
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
	public Dimension render(Graphics2D g)
	{
		if (!config.enabledOverlay() || config.timelineStyle() != TimelineStyle.CIRCULAR)
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
		boolean compact = config.mode() == OverlayMode.COMPACT;

		int diameter = scale(TickFlowLayout.BASE_CELL, scalePct);
		// +15% then another +10% so NOW reads clearly on a fast timeline.
		int nowDiameter = TickFlowLayout.nowSize(diameter);
		int gap = scale(TickFlowLayout.BASE_GAP, scalePct);
		int pad = scale(8, scalePct);
		int headerH = scale(16, scalePct);
		int sectionH = compact ? 0 : scale(18, scalePct);
		int corner = scale(12, scalePct);
		CycleFeedback feedback = config.showCycleFeedback() ? snap.getCycleFeedback() : null;
		boolean showCycleHud = config.showReadiness() || feedback != null;
		int cycleHudH = showCycleHud ? scale(42, scalePct) : 0;
		int cycleHudGap = showCycleHud ? scale(4, scalePct) : 0;

		int contentW = 0;
		for (int i = 0; i < slotCount; i++)
		{
			contentW += diameter;
			if (i < slotCount - 1)
			{
				contentW += gap;
			}
		}
		contentW += nowDiameter - diameter;

		int width = contentW + pad * 2;
		int height = pad + headerH + gap + nowDiameter + sectionH
			+ cycleHudGap + cycleHudH + pad;

		Object oldAa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

		Font titleFont = FontManager.getRunescapeBoldFont();
		Font bodyFont = FontManager.getRunescapeFont();
		Font smallFont = FontManager.getRunescapeSmallFont();

		scratchFrame.setRoundRect(0, 0, width - 1, height - 1, corner, corner);
		g.setColor(PANEL_BG);
		g.fill(scratchFrame);
		g.setColor(PANEL_BORDER);
		g.draw(scratchFrame);

		int y = pad;
		g.setFont(titleFont);
		g.setColor(TITLE);
		g.drawString("TickFlow", pad, y + ascent(g));

		int btnSize = scale(12, scalePct);
		int btnGap = scale(6, scalePct);
		int muteX = width - pad - btnSize;
		int cursorX = muteX;
		int btnY = y + 1;
		boolean soundOn = plugin.isTickSoundEnabled();
		muteButtonBounds.setBounds(muteX - 2, btnY - 2, btnSize + 4, btnSize + 4);
		drawMuteButton(g, muteX, btnY, btnSize, plugin.isTickSoundAudible());
		if (soundOn)
		{
			cursorX = muteX - btnGap - btnSize;
			volumeButtonBounds.setBounds(cursorX - 2, btnY - 2, btnSize + 4, btnSize + 4);
			drawVolumeButton(g, cursorX, btnY, btnSize, plugin.getTickSoundVolume(), plugin.isTickSoundAudible());
		}
		else
		{
			volumeButtonBounds.setBounds(0, 0, 0, 0);
		}
		int styleX = cursorX - btnGap - btnSize;
		styleButtonBounds.setBounds(styleX - 2, btnY - 2, btnSize + 4, btnSize + 4);
		drawStyleButton(g, styleX, btnY, btnSize, TimelineStyle.CIRCULAR);

		y += headerH + gap;

		List<CircleSlot> slots = slotsFor(snap, slotCount);
		double tickProgress = plugin.getTickProgress();
		int rowH = nowDiameter;
		int x = pad;
		int[] slotXs = new int[slots.size()];
		int[] slotDs = new int[slots.size()];
		int nowIndex = -1;

		for (int i = 0; i < slots.size(); i++)
		{
			CircleSlot slot = slots.get(i);
			boolean now = slot.relative == 0;
			int d = now ? nowDiameter : diameter;
			slotXs[i] = x;
			slotDs[i] = d;
			if (now)
			{
				nowIndex = i;
			}
			int cellY = y + (rowH - d) / 2;
			int cx = x + d / 2;

			Color fill;
			Color ring;
			Color text;
			if (slot.relative < 0)
			{
				fill = PAST_FILL;
				ring = PAST_RING;
				text = PAST_TEXT;
			}
			else if (now)
			{
				fill = NOW_FILL;
				ring = NOW_RING;
				text = NOW_TEXT;
			}
			else if (slot.readyMarker)
			{
				fill = READY_FILL;
				ring = READY_RING;
				text = NEXT_TEXT;
			}
			else if (!slot.type.isEmpty())
			{
				fill = NEXT_FILL;
				ring = NEXT_RING;
				text = NEXT_TEXT;
			}
			else
			{
				fill = NEXT_IDLE_FILL;
				ring = NEXT_IDLE_RING;
				text = NEXT_IDLE_TEXT;
			}

			g.setColor(fill);
			g.fillOval(x, cellY, d, d);

			Stroke oldStroke = g.getStroke();
			g.setStroke(ringStroke(d));
			g.setColor(ring);
			g.drawOval(x + 1, cellY + 1, d - 2, d - 2);

			if (now)
			{
				// Icon under the swipe so the dark wedge reads like a WoW cooldown.
				drawIcon(g, slot, x, cellY, d);
				double p = clamp01(tickProgress);
				drawNowProgress(g, x, cellY, d, cx, cellY + d / 2, p);
			}
			g.setStroke(oldStroke);

			g.setFont(smallFont);
			g.setColor(text);
			FontMetrics fm = g.getFontMetrics();
			String idx = formatRelative(slot.relative);
			g.drawString(idx, cx - fm.stringWidth(idx) / 2, cellY + fm.getAscent() + 2);

			if (!now)
			{
				drawIcon(g, slot, x, cellY, d);
			}

			x += d + gap;
		}

		y += rowH;

		if (!compact)
		{
			g.setFont(smallFont);
			drawZoneHeaders(g, slots, slotXs, slotDs, nowIndex, y);
			y += sectionH;
		}

		if (showCycleHud)
		{
			y += cycleHudGap;
			drawCycleHud(g, snap, feedback, pad, y, contentW, cycleHudH, tickProgress, bodyFont, smallFont);
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
		if (cachedDimension == null || cachedWidth != width || cachedHeight != height)
		{
			cachedWidth = width;
			cachedHeight = height;
			cachedDimension = new Dimension(width, height);
		}
		return cachedDimension;
	}

	/**
	 * WoW-style NOW progress: dark clockwise pie over the cell, soft wash on the
	 * remaining slice, and a yellow→green outer arc + tip spark matching the same angle.
	 */
	private void drawNowProgress(Graphics2D g, int x, int y, int d, int cx, int cy, double progress)
	{
		float baseW = Math.max(2.5f, d / 12f);
		float progressW = baseW * 1.4f;
		int inset = Math.max(3, Math.round(baseW) + 1);
		int ix = x + inset;
		int iy = y + inset;
		int id = d - inset * 2;
		double extent = -progress * 360.0;
		double remainExtent = -(1.0 - progress) * 360.0;
		double tipAngle = 90.0 + extent;
		Color tip = TickFlowLayout.progressColor(progress);

		Stroke old = g.getStroke();

		if (progress < 0.999 && remainExtent < -0.5)
		{
			g.setColor(NOW_REMAIN_WASH);
			scratchArc.setArc(ix, iy, id, id, tipAngle, remainExtent, Arc2D.PIE);
			g.fill(scratchArc);
		}

		if (progress > 0.001)
		{
			g.setColor(NOW_SWIPE);
			scratchArc.setArc(ix, iy, id, id, 90, extent, Arc2D.PIE);
			g.fill(scratchArc);

			double rad = Math.toRadians(tipAngle);
			// Hand stops short of the ring so the tip reads lighter.
			double handR = id / 2.0 * 0.88;
			int tipX = (int) Math.round(cx + handR * Math.cos(rad));
			int tipY = (int) Math.round(cy - handR * Math.sin(rad));
			g.setStroke(handStroke(Math.max(1.0f, progressW * 0.28f)));
			g.setColor(NOW_HAND);
			g.drawLine(cx, cy, tipX, tipY);

			g.setStroke(nowProgressStroke(progressW));
			g.setColor(tip);
			scratchArc.setArc(x + 1, y + 1, d - 2, d - 2, 90, extent, Arc2D.OPEN);
			g.draw(scratchArc);

			double radius = (d - 2) / 2.0;
			int sparkX = (int) Math.round(cx + radius * Math.cos(rad));
			int sparkY = (int) Math.round(cy - radius * Math.sin(rad));
			int spark = Math.max(2, Math.round(progressW * 0.85f));
			g.setColor(NOW_SPARK);
			g.fillOval(sparkX - spark / 2, sparkY - spark / 2, spark, spark);
			int core = Math.max(1, spark / 2);
			g.setColor(NOW_SPARK_CORE);
			g.fillOval(sparkX - core / 2, sparkY - core / 2, core, core);
		}
		g.setStroke(old);
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
		g.setColor(audible ? NOW_PROGRESS : MUTED);
		int bodyW = Math.max(2, size / 3);
		int bodyH = Math.max(4, size * 2 / 3);
		int bodyX = x;
		int bodyY = y + (size - bodyH) / 2;
		g.fillRect(bodyX, bodyY + bodyH / 4, bodyW, bodyH / 2);
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
		Color on = audible ? NOW_PROGRESS : MUTED;
		for (int i = 0; i < bars; i++)
		{
			int barH = Math.max(3, (size * (i + 1)) / bars);
			int bx = x + i * (barW + gap);
			int by = y + size - barH;
			g.setColor(i < lit ? on : VOLUME_BAR_OFF);
			g.fillRect(bx, by, barW, barH);
		}
	}

	private void drawZoneHeaders(Graphics2D g, List<CircleSlot> slots, int[] slotXs, int[] slotDs, int nowIndex, int y)
	{
		int textY = y + ascent(g) + 4;
		if (nowIndex > 0)
		{
			int x0 = slotXs[0];
			int x1 = slotXs[nowIndex - 1] + slotDs[nowIndex - 1];
			drawZoneHeader(g, "PAST", PAST_TEXT, x0, x1, textY, Align.LEFT);
		}
		if (nowIndex >= 0)
		{
			int x0 = slotXs[nowIndex];
			int x1 = x0 + slotDs[nowIndex];
			drawZoneHeader(g, "NOW", NOW_TEXT, x0, x1, textY, Align.CENTER);
		}
		if (nowIndex >= 0 && nowIndex < slots.size() - 1)
		{
			int x0 = slotXs[nowIndex + 1];
			int x1 = slotXs[slots.size() - 1] + slotDs[slots.size() - 1];
			boolean nextActive = false;
			for (int i = nowIndex + 1; i < slots.size(); i++)
			{
				CircleSlot s = slots.get(i);
				if (s.readyMarker || !s.type.isEmpty())
				{
					nextActive = true;
					break;
				}
			}
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

	private void drawIcon(Graphics2D g, CircleSlot slot, int x, int y, int diameter)
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
			if (icon == null && type == ActionType.PRAYER)
			{
				icon = plugin.getIcons().get(ActionType.PRAYER);
			}
		}
		else
		{
			icon = plugin.getIcons().get(type);
		}

		if (icon == null)
		{
			return;
		}
		int ix = x + (diameter - icon.getWidth()) / 2;
		int iy = y + (diameter - icon.getHeight()) / 2 + 2;
		g.drawImage(icon, ix, iy, null);
	}

	private List<CircleSlot> slotsFor(TickFlowState.Snapshot snap, int slotCount)
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

	private List<CircleSlot> buildSlots(TickFlowState.Snapshot snap, int slotCount)
	{
		int before = slotCount / 2;
		int after = slotCount - before - 1;
		List<TickRecord> history = snap.getHistory();
		TickRecord current = snap.getCurrentTick();
		List<CircleSlot> slots = new ArrayList<>(slotCount);

		for (int rel = -before; rel <= after; rel++)
		{
			if (rel < 0)
			{
				int histIndex = history.size() + rel;
				if (histIndex >= 0 && histIndex < history.size())
				{
					slots.add(CircleSlot.fromRecord(rel, history.get(histIndex)));
				}
				else
				{
					slots.add(CircleSlot.empty(rel));
				}
			}
			else if (rel == 0)
			{
				if (current != null)
				{
					slots.add(CircleSlot.fromRecord(0, current));
				}
				else if (!history.isEmpty())
				{
					slots.add(CircleSlot.fromRecord(0, history.get(history.size() - 1)));
				}
				else
				{
					slots.add(CircleSlot.empty(0));
				}
			}
			else
			{
				boolean readyMarker = config.showReadiness()
					&& snap.getConfidence().allowsReadiness()
					&& snap.getTicksUntilReady() == rel;
				slots.add(new CircleSlot(
					rel,
					readyMarker ? ActionType.ATTACK : ActionType.EMPTY,
					readyMarker ? "Ready" : "",
					readyMarker,
					TickAction.NO_SPRITE));
			}
		}
		return slots;
	}

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
	}

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

	private Stroke ringStroke(int diameter)
	{
		float w = Math.max(2.5f, diameter / 12f);
		if (cachedStroke == null || Float.compare(cachedStrokeW, w) != 0)
		{
			cachedStrokeW = w;
			cachedStroke = new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		}
		return cachedStroke;
	}

	private Stroke nowProgressStroke(float width)
	{
		if (cachedNowStroke == null || Float.compare(cachedNowStrokeW, width) != 0)
		{
			cachedNowStrokeW = width;
			cachedNowStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		}
		return cachedNowStroke;
	}

	private Stroke handStroke(float width)
	{
		if (cachedHandStroke == null || Float.compare(cachedHandStrokeW, width) != 0)
		{
			cachedHandStrokeW = width;
			cachedHandStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		}
		return cachedHandStroke;
	}

	private Stroke cycleStroke(int diameter)
	{
		float w = Math.max(2.5f, diameter / 10f);
		if (cachedCycleStroke == null || Float.compare(cachedCycleStrokeW, w) != 0)
		{
			cachedCycleStrokeW = w;
			cachedCycleStroke = new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		}
		return cachedCycleStroke;
	}

	private static String formatRelative(int relative)
	{
		int idx = relative + 4;
		if (idx >= 0 && idx < RELATIVE_LABELS.length)
		{
			return RELATIVE_LABELS[idx];
		}
		if (relative > 0)
		{
			return "+" + relative;
		}
		return Integer.toString(relative);
	}

	private static int scale(int base, int scalePct)
	{
		return Math.max(1, (base * scalePct + 50) / 100);
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

	private static int ascent(Graphics2D g)
	{
		return g.getFontMetrics().getAscent();
	}

	private static double clamp01(double v)
	{
		if (v < 0)
		{
			return 0;
		}
		if (v > 1)
		{
			return 1;
		}
		return v;
	}

	private static final class CircleSlot
	{
		private final int relative;
		private final ActionType type;
		private final String label;
		private final boolean readyMarker;
		private final int spriteId;

		private CircleSlot(int relative, ActionType type, String label, boolean readyMarker, int spriteId)
		{
			this.relative = relative;
			this.type = type;
			this.label = label;
			this.readyMarker = readyMarker;
			this.spriteId = spriteId;
		}

		static CircleSlot empty(int relative)
		{
			return new CircleSlot(relative, ActionType.EMPTY, "", false, TickAction.NO_SPRITE);
		}

		static CircleSlot fromRecord(int relative, TickRecord record)
		{
			TickAction primary = record.getPrimaryAction();
			boolean readyOnly = record.isAttackReady() && primary.getType() == ActionType.EMPTY;
			return new CircleSlot(
				relative,
				readyOnly ? ActionType.ATTACK : primary.getType(),
				readyOnly ? "Ready" : primary.getLabel(),
				readyOnly,
				primary.getSpriteId());
		}
	}
}
