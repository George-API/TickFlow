package com.tickflow;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * One observation assigned to a game tick.
 */
public final class TickAction
{
	public static final int NO_SPRITE = -1;

	private final ActionType type;
	private final ActionSource source;
	private final String label;
	private final int confidence;
	private final int order;
	@Nullable
	private final String metadata;
	private final int spriteId;

	public TickAction(ActionType type, ActionSource source, String label, int confidence, int order)
	{
		this(type, source, label, confidence, order, null, NO_SPRITE);
	}

	public TickAction(
		ActionType type,
		ActionSource source,
		String label,
		int confidence,
		int order,
		@Nullable String metadata)
	{
		this(type, source, label, confidence, order, metadata, NO_SPRITE);
	}

	public TickAction(
		ActionType type,
		ActionSource source,
		String label,
		int confidence,
		int order,
		@Nullable String metadata,
		int spriteId)
	{
		this.type = Objects.requireNonNull(type, "type");
		this.source = Objects.requireNonNull(source, "source");
		this.label = label == null || label.isEmpty() ? type.getShortLabel() : label;
		this.confidence = clampConfidence(confidence);
		this.order = order;
		this.metadata = metadata;
		this.spriteId = spriteId;
	}

	private static int clampConfidence(int confidence)
	{
		if (confidence < 0)
		{
			return 0;
		}
		if (confidence > 100)
		{
			return 100;
		}
		return confidence;
	}

	public ActionType getType()
	{
		return type;
	}

	public ActionSource getSource()
	{
		return source;
	}

	public String getLabel()
	{
		return label;
	}

	public int getConfidence()
	{
		return confidence;
	}

	public int getOrder()
	{
		return order;
	}

	@Nullable
	public String getMetadata()
	{
		return metadata;
	}

	public int getSpriteId()
	{
		return spriteId;
	}

	public boolean hasSpriteId()
	{
		return spriteId >= 0;
	}

	public boolean isStrongerThan(TickAction other)
	{
		if (other == null)
		{
			return true;
		}
		if (type.beats(other.type))
		{
			return true;
		}
		if (other.type.beats(type))
		{
			return false;
		}
		if (confidence != other.confidence)
		{
			return confidence > other.confidence;
		}
		if (hasSpriteId() != other.hasSpriteId())
		{
			return hasSpriteId();
		}
		if (hasSpriteId() && spriteId != other.spriteId)
		{
			// Prefer a specific prayer sprite over the neutral orb placeholder.
			boolean thisGeneric = spriteId == PrayerSprites.GENERIC_SPRITE;
			boolean otherGeneric = other.spriteId == PrayerSprites.GENERIC_SPRITE;
			if (thisGeneric != otherGeneric)
			{
				return !thisGeneric;
			}
		}
		return order < other.order;
	}

	@Override
	public String toString()
	{
		return type + "(" + label + ", conf=" + confidence + ", src=" + source + ")";
	}
}
