package au.net.causal.maven.plugins.autojdk.xml.config;

import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlValue;

import javax.xml.datatype.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Configuration for the setting that controls when a check should be performed for an existing local JDK whether a new version exists.
 */
public interface JdkUpdatePolicy
{
    /**
     * Returns whether a remote check for updates should be performed.
     *
     * @param lastCheckTime the time the last update check was performed previously.  May be null if no previous update check was performed.
     * @param now the current time, never null.
     *
     * @return true if the update check should be performed, false if not.
     */
    public boolean isUpdateCheckRequired(Instant lastCheckTime, Instant now);

    /**
     * Never perform update checks.
     */
    public static class Never implements JdkUpdatePolicy
    {
        @Override
        public boolean isUpdateCheckRequired(Instant lastCheckTime, Instant now)
        {
            return false;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            return obj instanceof Never;
        }

        @Override
        public int hashCode()
        {
            return Never.class.hashCode();
        }
    }

    /**
     * Always perform update checks.
     */
    public static class Always implements JdkUpdatePolicy
    {
        @Override
        public boolean isUpdateCheckRequired(Instant lastCheckTime, Instant now)
        {
            return true;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            return obj instanceof Always;
        }

        @Override
        public int hashCode()
        {
            return Always.class.hashCode();
        }
    }

    /**
     * Perform updates when the previous check was performed longer ago than the specified duration.
     */
    public static class EveryDuration implements JdkUpdatePolicy
    {
        private Duration value;

        public EveryDuration()
        {
        }

        public EveryDuration(Duration value)
        {
            this.value = value;
        }

        /**
         * @return the amount of time that needs to have passed from the previous check before checking again.
         */
        @XmlValue
        public Duration getValue()
        {
            return value;
        }

        public void setValue(Duration value)
        {
            this.value = value;
        }

        @XmlTransient
        public java.time.Duration getValueAsDuration()
        {
            if (value == null)
                return null;

            java.time.Duration d = java.time.Duration.ZERO;

            //These two are estimations but probably good enough if a user really wants to use them
            d = d.plus(ChronoUnit.YEARS.getDuration().multipliedBy(value.getYears()));
            d = d.plus(ChronoUnit.MONTHS.getDuration().multipliedBy(value.getMonths()));

            d = d.plusDays(value.getDays());
            d = d.plusHours(value.getHours());
            d = d.plusMinutes(value.getMinutes());
            d = d.plusSeconds(value.getSeconds());

            return d;
        }

        @Override
        public boolean isUpdateCheckRequired(Instant lastCheckTime, Instant now)
        {
            //If value was not specified, just assume zero and always check
            if (value == null)
                return true;

            //If never checked before, then we'll need to check no matter what the current time is
            if (lastCheckTime == null)
                return true;

            return lastCheckTime.plus(getValueAsDuration()).isBefore(now);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof EveryDuration)) return false;
            EveryDuration that = (EveryDuration) o;
            return Objects.equals(getValue(), that.getValue());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getValue());
        }
    }
}
