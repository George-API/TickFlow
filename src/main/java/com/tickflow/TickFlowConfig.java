package com.tickflow;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(TickFlowConfig.GROUP)
public interface TickFlowConfig extends Config
{
	String GROUP = "tickflow";

	@ConfigItem(
		keyName = "enabledOverlay",
		name = "Enable overlay",
		description = "Show the TickFlow timeline overlay",
		position = 0
	)
	default boolean enabledOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mode",
		name = "Overlay mode",
		description = "Learn: full timeline + headers. Compact: timeline without headers. Minimal: NOW tick only with style/volume controls.",
		position = 1
	)
	default OverlayMode mode()
	{
		return OverlayMode.LEARN;
	}

	@ConfigItem(
		keyName = "timelineStyle",
		name = "Timeline style",
		description = "Circular cells with a NOW progress ring (default), or classic square cells",
		position = 2
	)
	default TimelineStyle timelineStyle()
	{
		return TimelineStyle.CIRCULAR;
	}

	@ConfigItem(
		keyName = "showReadiness",
		name = "Show inferred attack readiness",
		description = "Show a circular attack-cycle cooldown with remaining ticks (WoW-style)",
		position = 3
	)
	default boolean showReadiness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCycleFeedback",
		name = "Show cycle feedback",
		description = "Brief OK / +N badge after a completed attack cycle",
		position = 4
	)
	default boolean showCycleFeedback()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTickPulse",
		name = "Show tick pulse",
		description = "Square style only: thin progress strip inside the NOW cell",
		position = 5
	)
	default boolean showTickPulse()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tickSound",
		name = "Tick sound",
		description = "Play a soft metronome blip each game tick",
		position = 6
	)
	default boolean tickSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tickSoundMuted",
		name = "Mute tick sound",
		description = "Mute the metronome without disabling the feature (also toggled from the overlay speaker button)",
		position = 7
	)
	default boolean tickSoundMuted()
	{
		return false;
	}

	@Range(min = 10, max = 100)
	@ConfigItem(
		keyName = "tickSoundVolume",
		name = "Tick sound volume",
		description = "Metronome volume (kept quiet by default)",
		position = 8
	)
	default int tickSoundVolume()
	{
		return 40;
	}

	@Range(min = 5, max = 8)
	@ConfigItem(
		keyName = "timelineLength",
		name = "Timeline length",
		description = "Number of tick slots shown (5–8)",
		position = 9
	)
	default int timelineLength()
	{
		return 5;
	}

	@Range(min = 80, max = 140)
	@ConfigItem(
		keyName = "overlayScale",
		name = "Overlay scale %",
		description = "Scale the overlay size",
		position = 10
	)
	default int overlayScale()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "autoHideOutsideCombat",
		name = "Hide outside combat",
		description = "Hide the overlay when not recently in combat",
		position = 11
	)
	default boolean autoHideOutsideCombat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugMode",
		name = "Debug diagnostics",
		description = "Show extra timing and observation diagnostics (off by default)",
		position = 12
	)
	default boolean debugMode()
	{
		return false;
	}
}
