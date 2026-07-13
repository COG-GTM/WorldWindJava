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

import static org.junit.Assert.*;

import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.Asset;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.AssetExposure;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.CoverageReport;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.Threat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class ThreatCoverageAnalyzerTest
{
    private static final double DELTA = 1e-6;

    /** A spherical Earth radius used by the analyzer under test. */
    private static final double GLOBE_RADIUS = ThreatCoverageAnalyzer.DEFAULT_GLOBE_RADIUS_METERS;

    private final ThreatCoverageAnalyzer analyzer = new ThreatCoverageAnalyzer(GLOBE_RADIUS);

    /**
     * Places a location a precise ground distance from a center, using the same spherical radius the analyzer uses so
     * the resulting distance is exact.
     */
    private LatLon locationAtDistance(LatLon center, double azimuthDegrees, double distanceMeters)
    {
        double angularDistanceRadians = distanceMeters / GLOBE_RADIUS;
        return LatLon.greatCircleEndPosition(center,
            gov.nasa.worldwind.geom.Angle.fromDegrees(azimuthDegrees).radians, angularDistanceRadians);
    }

    @Test
    public void testDistanceMetersRoundTrip()
    {
        LatLon center = LatLon.fromDegrees(54.63, 21.79);
        LatLon point = this.locationAtDistance(center, 45.0, 100000.0); // 100 km to the NE
        assertEquals("Great-circle distance should round-trip", 100000.0,
            this.analyzer.distanceMeters(center, point), 1.0);
    }

    @Test
    public void testAssetJustInsideRingIsExposed()
    {
        LatLon threatLoc = LatLon.fromDegrees(54.63, 21.79);
        Threat threat = new Threat("SAM", "SAM_LONG", threatLoc, 100000.0); // 100 km WEZ

        // 99 km from the threat: just inside the ring.
        LatLon assetLoc = this.locationAtDistance(threatLoc, 90.0, 99000.0);
        assertTrue("Asset 1 km inside the ring must be exposed",
            this.analyzer.isWithinThreat(assetLoc, threat));
    }

    @Test
    public void testAssetJustOutsideRingIsNotExposed()
    {
        LatLon threatLoc = LatLon.fromDegrees(54.63, 21.79);
        Threat threat = new Threat("SAM", "SAM_LONG", threatLoc, 100000.0); // 100 km WEZ

        // 101 km from the threat: just outside the ring.
        LatLon assetLoc = this.locationAtDistance(threatLoc, 90.0, 101000.0);
        assertFalse("Asset 1 km outside the ring must not be exposed",
            this.analyzer.isWithinThreat(assetLoc, threat));
    }

    @Test
    public void testAssetOnRingBoundaryIsExposed()
    {
        LatLon threatLoc = LatLon.fromDegrees(0.0, 0.0);
        Threat threat = new Threat("SAM", "SAM_MEDIUM", threatLoc, 50000.0); // 50 km WEZ

        // Exactly on the boundary. The boundary counts as inside (<=).
        LatLon assetLoc = this.locationAtDistance(threatLoc, 0.0, 50000.0);
        assertEquals("Boundary distance should equal the radius", 50000.0,
            this.analyzer.distanceMeters(assetLoc, threatLoc), 0.5);
        assertTrue("Asset exactly on the ring boundary must be exposed",
            this.analyzer.isWithinThreat(assetLoc, threat));
    }

    @Test
    public void testMultipleOverlappingThreats()
    {
        // Two threats 60 km apart, each with a 100 km WEZ, so their rings overlap.
        LatLon threatLocA = LatLon.fromDegrees(54.60, 21.80);
        LatLon threatLocB = this.locationAtDistance(threatLocA, 90.0, 60000.0);
        Threat threatA = new Threat("SAM-A", "SAM_LONG", threatLocA, 100000.0);
        Threat threatB = new Threat("SAM-B", "SAM_LONG", threatLocB, 100000.0);

        // An asset midway between the two threats (30 km from each) sits inside both rings.
        LatLon assetLoc = this.locationAtDistance(threatLocA, 90.0, 30000.0);
        Asset asset = new Asset("Overlap Node", "C2", assetLoc);

        AssetExposure exposure = this.analyzer.analyzeAsset(asset, Arrays.asList(threatA, threatB));
        assertTrue("Asset in the overlap must be exposed", exposure.isExposed());
        assertEquals("Asset must be exposed by both threats", 2, exposure.getExposingThreats().size());
        assertTrue(exposure.getExposingThreats().contains(threatA));
        assertTrue(exposure.getExposingThreats().contains(threatB));
    }

    @Test
    public void testAssetExposedByOnlyOneOfTwoThreats()
    {
        LatLon threatLocA = LatLon.fromDegrees(54.60, 21.80);
        Threat threatA = new Threat("SAM-A", "SAM_LONG", threatLocA, 100000.0);

        // A far-away threat that cannot reach the asset.
        LatLon threatLocB = this.locationAtDistance(threatLocA, 90.0, 500000.0);
        Threat threatB = new Threat("SAM-B", "SAM_SHORT", threatLocB, 50000.0);

        LatLon assetLoc = this.locationAtDistance(threatLocA, 90.0, 40000.0); // inside A only
        Asset asset = new Asset("Node", "MANEUVER", assetLoc);

        AssetExposure exposure = this.analyzer.analyzeAsset(asset, Arrays.asList(threatA, threatB));
        assertTrue(exposure.isExposed());
        assertEquals("Only the reaching threat should be listed", 1, exposure.getExposingThreats().size());
        assertEquals("SAM-A", exposure.getExposingThreats().get(0).getName());
    }

    @Test
    public void testCoveredAssetHasNoExposingThreats()
    {
        LatLon threatLoc = LatLon.fromDegrees(54.63, 21.79);
        Threat threat = new Threat("SAM", "SAM_LONG", threatLoc, 100000.0);

        LatLon assetLoc = this.locationAtDistance(threatLoc, 180.0, 250000.0); // 250 km away
        Asset asset = new Asset("Rear Node", "SUSTAINMENT", assetLoc);

        AssetExposure exposure = this.analyzer.analyzeAsset(asset, Collections.singletonList(threat));
        assertFalse("Asset well outside the ring must be covered", exposure.isExposed());
        assertTrue(exposure.getExposingThreats().isEmpty());
    }

    @Test
    public void testCoverageReportCounts()
    {
        LatLon threatLoc = LatLon.fromDegrees(54.63, 21.79);
        Threat threat = new Threat("SAM", "SAM_LONG", threatLoc, 100000.0);

        Asset exposed1 = new Asset("Exposed 1", "C2", this.locationAtDistance(threatLoc, 0.0, 20000.0));
        Asset exposed2 = new Asset("Exposed 2", "MANEUVER", this.locationAtDistance(threatLoc, 120.0, 80000.0));
        Asset covered = new Asset("Covered", "SUSTAINMENT", this.locationAtDistance(threatLoc, 240.0, 300000.0));

        List<Asset> assets = Arrays.asList(exposed1, exposed2, covered);
        CoverageReport report = this.analyzer.analyze(assets, Collections.singletonList(threat));

        assertEquals(3, report.getTotalCount());
        assertEquals(2, report.getExposedCount());
        assertEquals(1, report.getCoveredCount());

        String summary = report.formatSummary();
        assertNotNull(summary);
        assertTrue("Summary should report the exposed-of-total ratio", summary.contains("2 of 3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeGlobeRadiusRejected()
    {
        new ThreatCoverageAnalyzer(-1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeThreatRadiusRejected()
    {
        new Threat("Bad", "SAM_LONG", LatLon.fromDegrees(0, 0), -5.0);
    }
}
