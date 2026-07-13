/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 * 
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * 
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 * 
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 * 
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */

package gov.nasa.worldwindx.examples.symbology;

import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.util.Logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes air-defense protection coverage for a protection cell common operating picture. Given a set of friendly
 * assets and a set of hostile air-defense threats (each with a weapon-engagement-zone radius), the analyzer determines
 * which assets fall inside one or more threat rings and is therefore exposed.
 * <p>
 * The geometry uses {@link LatLon#greatCircleDistance(LatLon, LatLon)} on a spherical Earth of the configured radius, so
 * the exposure decision is consistent with the range rings rendered by
 * {@link AirDefenseCoverage}. This class performs no rendering and has no WorldWind runtime dependencies beyond the
 * geometry package, which makes it straightforward to unit test.
 */
public class ThreatCoverageAnalyzer
{
    /** Default globe radius, in meters, used when none is supplied. Matches WorldWind's Earth equatorial radius. */
    public static final double DEFAULT_GLOBE_RADIUS_METERS = Earth.WGS84_EQUATORIAL_RADIUS;

    /** Radius of the sphere, in meters, used to convert great-circle angular distances to ground distances. */
    protected final double globeRadiusMeters;

    /** Creates an analyzer using {@link #DEFAULT_GLOBE_RADIUS_METERS}. */
    public ThreatCoverageAnalyzer()
    {
        this(DEFAULT_GLOBE_RADIUS_METERS);
    }

    /**
     * Creates an analyzer that measures ground distance on a sphere of the specified radius.
     *
     * @param globeRadiusMeters radius of the sphere in meters. Must be greater than zero.
     *
     * @throws IllegalArgumentException if {@code globeRadiusMeters} is not greater than zero.
     */
    public ThreatCoverageAnalyzer(double globeRadiusMeters)
    {
        if (globeRadiusMeters <= 0)
        {
            String message = Logging.getMessage("generic.ArgumentOutOfRange", globeRadiusMeters);
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.globeRadiusMeters = globeRadiusMeters;
    }

    /**
     * Indicates the sphere radius used to convert angular distances to ground distances.
     *
     * @return the globe radius in meters.
     */
    public double getGlobeRadiusMeters()
    {
        return this.globeRadiusMeters;
    }

    /**
     * Computes the great-circle ground distance between two locations.
     *
     * @param a the first location.
     * @param b the second location.
     *
     * @return the distance between the two locations, in meters.
     *
     * @throws IllegalArgumentException if either location is null.
     */
    public double distanceMeters(LatLon a, LatLon b)
    {
        if (a == null || b == null)
        {
            String message = Logging.getMessage("nullValue.LatLonIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        Angle angle = LatLon.greatCircleDistance(a, b);
        return angle.radians * this.globeRadiusMeters;
    }

    /**
     * Determines whether a location falls within a threat's weapon-engagement zone. A location exactly on the ring
     * boundary is considered inside the zone.
     *
     * @param location the location to test.
     * @param threat   the threat whose weapon-engagement zone is tested.
     *
     * @return {@code true} if the location is inside or on the threat ring, otherwise {@code false}.
     *
     * @throws IllegalArgumentException if either argument is null.
     */
    public boolean isWithinThreat(LatLon location, Threat threat)
    {
        if (location == null)
        {
            String message = Logging.getMessage("nullValue.LatLonIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        if (threat == null)
        {
            String message = Logging.getMessage("nullValue.ObjectIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        return this.distanceMeters(location, threat.getLocation()) <= threat.getRadiusMeters();
    }

    /**
     * Computes the exposure of a single asset against a collection of threats.
     *
     * @param asset   the asset to evaluate.
     * @param threats the threats to evaluate the asset against.
     *
     * @return an {@link AssetExposure} describing whether the asset is exposed and by which threats.
     *
     * @throws IllegalArgumentException if either argument is null.
     */
    public AssetExposure analyzeAsset(Asset asset, Iterable<Threat> threats)
    {
        if (asset == null)
        {
            String message = Logging.getMessage("nullValue.ObjectIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        if (threats == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        List<Threat> exposingThreats = new ArrayList<Threat>();
        for (Threat threat : threats)
        {
            if (threat != null && this.isWithinThreat(asset.getLocation(), threat))
                exposingThreats.add(threat);
        }

        return new AssetExposure(asset, exposingThreats);
    }

    /**
     * Computes the full protection-coverage report for a set of assets against a set of threats.
     *
     * @param assets  the friendly assets to evaluate.
     * @param threats the hostile threats to evaluate the assets against.
     *
     * @return a {@link CoverageReport} summarizing per-asset exposure.
     *
     * @throws IllegalArgumentException if either argument is null.
     */
    public CoverageReport analyze(Iterable<Asset> assets, Iterable<Threat> threats)
    {
        if (assets == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        if (threats == null)
        {
            String message = Logging.getMessage("nullValue.IterableIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        // Snapshot threats so they can be traversed once per asset without exhausting a single-use Iterable.
        List<Threat> threatList = new ArrayList<Threat>();
        for (Threat threat : threats)
        {
            if (threat != null)
                threatList.add(threat);
        }

        List<AssetExposure> exposures = new ArrayList<AssetExposure>();
        for (Asset asset : assets)
        {
            if (asset != null)
                exposures.add(this.analyzeAsset(asset, threatList));
        }

        return new CoverageReport(exposures);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Data model
    // -----------------------------------------------------------------------------------------------------------------

    /** A friendly asset with a location and a doctrinal type. */
    public static class Asset
    {
        protected final String name;
        protected final String type;
        protected final LatLon location;

        /**
         * Creates a friendly asset.
         *
         * @param name     the asset's display name.
         * @param type     the asset's type (e.g. AIRFIELD, C2), used to select a symbol.
         * @param location the asset's geographic location.
         *
         * @throws IllegalArgumentException if {@code location} is null.
         */
        public Asset(String name, String type, LatLon location)
        {
            if (location == null)
            {
                String message = Logging.getMessage("nullValue.LatLonIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }

            this.name = name;
            this.type = type;
            this.location = location;
        }

        public String getName()
        {
            return this.name;
        }

        public String getType()
        {
            return this.type;
        }

        public LatLon getLocation()
        {
            return this.location;
        }
    }

    /** A hostile air-defense threat with a location and a weapon-engagement-zone radius. */
    public static class Threat
    {
        protected final String name;
        protected final String type;
        protected final LatLon location;
        protected final double radiusMeters;

        /**
         * Creates a hostile threat.
         *
         * @param name         the threat's display name.
         * @param type         the threat's type (e.g. SAM_LONG), used to select a symbol.
         * @param location     the threat's geographic location.
         * @param radiusMeters the weapon-engagement-zone radius, in meters. Must be greater than or equal to zero.
         *
         * @throws IllegalArgumentException if {@code location} is null or {@code radiusMeters} is negative.
         */
        public Threat(String name, String type, LatLon location, double radiusMeters)
        {
            if (location == null)
            {
                String message = Logging.getMessage("nullValue.LatLonIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }
            if (radiusMeters < 0)
            {
                String message = Logging.getMessage("generic.ArgumentOutOfRange", radiusMeters);
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }

            this.name = name;
            this.type = type;
            this.location = location;
            this.radiusMeters = radiusMeters;
        }

        public String getName()
        {
            return this.name;
        }

        public String getType()
        {
            return this.type;
        }

        public LatLon getLocation()
        {
            return this.location;
        }

        public double getRadiusMeters()
        {
            return this.radiusMeters;
        }
    }

    /** The exposure result for a single asset: whether it is exposed and, if so, by which threats. */
    public static class AssetExposure
    {
        protected final Asset asset;
        protected final List<Threat> exposingThreats;

        /**
         * Creates an exposure result.
         *
         * @param asset           the evaluated asset.
         * @param exposingThreats the threats whose weapon-engagement zones cover the asset. May be empty.
         *
         * @throws IllegalArgumentException if either argument is null.
         */
        public AssetExposure(Asset asset, List<Threat> exposingThreats)
        {
            if (asset == null || exposingThreats == null)
            {
                String message = Logging.getMessage("nullValue.ObjectIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }

            this.asset = asset;
            this.exposingThreats = Collections.unmodifiableList(new ArrayList<Threat>(exposingThreats));
        }

        public Asset getAsset()
        {
            return this.asset;
        }

        /**
         * Indicates whether the asset is inside at least one threat ring.
         *
         * @return {@code true} if the asset is exposed, otherwise {@code false}.
         */
        public boolean isExposed()
        {
            return !this.exposingThreats.isEmpty();
        }

        /**
         * Indicates the threats whose weapon-engagement zones cover this asset.
         *
         * @return an unmodifiable list of exposing threats. Empty if the asset is covered (not exposed).
         */
        public List<Threat> getExposingThreats()
        {
            return this.exposingThreats;
        }
    }

    /** A protection-coverage report over a set of assets. */
    public static class CoverageReport
    {
        protected final List<AssetExposure> exposures;

        /**
         * Creates a coverage report.
         *
         * @param exposures the per-asset exposure results.
         *
         * @throws IllegalArgumentException if {@code exposures} is null.
         */
        public CoverageReport(List<AssetExposure> exposures)
        {
            if (exposures == null)
            {
                String message = Logging.getMessage("nullValue.ObjectIsNull");
                Logging.logger().severe(message);
                throw new IllegalArgumentException(message);
            }

            this.exposures = Collections.unmodifiableList(new ArrayList<AssetExposure>(exposures));
        }

        /**
         * Indicates the per-asset exposure results.
         *
         * @return an unmodifiable list of exposures.
         */
        public List<AssetExposure> getExposures()
        {
            return this.exposures;
        }

        /**
         * Indicates the total number of assets evaluated.
         *
         * @return the asset count.
         */
        public int getTotalCount()
        {
            return this.exposures.size();
        }

        /**
         * Indicates the number of exposed assets.
         *
         * @return the count of assets inside at least one threat ring.
         */
        public int getExposedCount()
        {
            int count = 0;
            for (AssetExposure exposure : this.exposures)
            {
                if (exposure.isExposed())
                    count += 1;
            }
            return count;
        }

        /**
         * Indicates the number of covered (not exposed) assets.
         *
         * @return the count of assets outside every threat ring.
         */
        public int getCoveredCount()
        {
            return this.getTotalCount() - this.getExposedCount();
        }

        /**
         * Builds a human-readable coverage summary suitable for logging or a status readout.
         *
         * @return the formatted summary.
         */
        public String formatSummary()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Protection Cell Air-Defense Coverage Summary\n");
            sb.append("============================================\n");
            sb.append(String.format("%d of %d assets EXPOSED (inside a hostile weapon-engagement zone).%n",
                this.getExposedCount(), this.getTotalCount()));
            sb.append("\n");

            for (AssetExposure exposure : this.exposures)
            {
                Asset asset = exposure.getAsset();
                if (exposure.isExposed())
                {
                    StringBuilder threatNames = new StringBuilder();
                    for (Threat threat : exposure.getExposingThreats())
                    {
                        if (threatNames.length() > 0)
                            threatNames.append(", ");
                        threatNames.append(threat.getName());
                    }
                    sb.append(String.format("  [EXPOSED] %-28s by: %s%n", asset.getName(), threatNames.toString()));
                }
                else
                {
                    sb.append(String.format("  [COVERED] %-28s (outside all threat rings)%n", asset.getName()));
                }
            }

            return sb.toString();
        }
    }
}
