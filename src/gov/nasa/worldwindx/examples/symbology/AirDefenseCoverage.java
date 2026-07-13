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

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwind.render.airspaces.AirspaceAttributes;
import gov.nasa.worldwind.render.airspaces.BasicAirspaceAttributes;
import gov.nasa.worldwind.render.airspaces.CappedCylinder;
import gov.nasa.worldwind.symbology.*;
import gov.nasa.worldwind.symbology.milstd2525.MilStd2525TacticalSymbol;
import gov.nasa.worldwind.util.*;
import gov.nasa.worldwindx.examples.ApplicationTemplate;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.Asset;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.AssetExposure;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.CoverageReport;
import gov.nasa.worldwindx.examples.symbology.ThreatCoverageAnalyzer.Threat;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

/**
 * Protection Cell Air-Defense Coverage. Renders a protection-cell common operating picture over a realistic area of
 * operations and computes air-defense protection coverage.
 * <p>
 * The example loads a small scenario ({@code config/AirDefenseCoverageScenario.csv}) describing friendly assets and
 * hostile air-defense threats, then:
 * <ul>
 * <li>renders every entity as a MIL-STD-2525 {@link MilStd2525TacticalSymbol} (friendly assets as friendly symbols,
 * threats as hostile air-defense symbols);</li>
 * <li>draws each threat's weapon-engagement zone as a translucent, hostile-red range volume
 * ({@link CappedCylinder});</li>
 * <li>uses {@link ThreatCoverageAnalyzer} to determine which assets fall inside a threat ring, flags exposed assets
 * with a red callout, and prints a coverage summary.</li>
 * </ul>
 * See the {@link TacticalSymbols} and {@link TacticalGraphics} examples for the underlying symbology building blocks.
 */
public class AirDefenseCoverage extends ApplicationTemplate
{
    /** Classpath location of the bundled scenario file. */
    protected static final String SCENARIO_RESOURCE = "config/AirDefenseCoverageScenario.csv";

    /** Lower and upper altitudes, in meters, of the rendered weapon-engagement-zone volumes. */
    protected static final double WEZ_VOLUME_FLOOR_METERS = 0.0;
    protected static final double WEZ_VOLUME_CEILING_METERS = 18000.0;

    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        protected RenderableLayer threatVolumeLayer;
        protected RenderableLayer symbolLayer;
        protected RenderableLayer statusLayer;
        protected TacticalSymbolAttributes sharedSymbolAttrs;
        protected TacticalSymbolAttributes sharedHighlightAttrs;

        public AppFrame()
        {
            super(true, true, false);

            // Layer that holds the translucent weapon-engagement-zone volumes. Rendered beneath the symbols.
            this.threatVolumeLayer = new RenderableLayer();
            this.threatVolumeLayer.setName("Threat Coverage (WEZ)");

            // Layer that holds the MIL-STD-2525 tactical symbols for assets and threats.
            this.symbolLayer = new RenderableLayer();
            this.symbolLayer.setName("Common Operating Picture");

            // Layer that holds exposure callouts for flagged assets.
            this.statusLayer = new RenderableLayer();
            this.statusLayer.setName("Exposure Callouts");

            // Shared symbol attributes so the whole picture scales consistently.
            this.sharedSymbolAttrs = new BasicTacticalSymbolAttributes();
            this.sharedHighlightAttrs = new BasicTacticalSymbolAttributes();
            this.sharedHighlightAttrs.setInteriorMaterial(Material.WHITE);
            this.sharedHighlightAttrs.setOpacity(1.0);

            try
            {
                // Load the scenario laydown from the bundled resource.
                Scenario scenario = loadScenario();

                // Draw threat rings first so symbols render on top of the translucent volumes.
                for (Threat threat : scenario.threats)
                {
                    this.addThreatVolume(threat);
                }

                // Draw a MIL-STD-2525 symbol for every entity in the picture.
                for (Asset asset : scenario.assets)
                {
                    this.addAssetSymbol(asset);
                }
                for (Threat threat : scenario.threats)
                {
                    this.addThreatSymbol(threat);
                }

                // Compute protection coverage and flag exposed assets.
                ThreatCoverageAnalyzer analyzer = new ThreatCoverageAnalyzer(
                    this.getWwd().getModel().getGlobe() != null
                        ? this.getWwd().getModel().getGlobe().getRadius()
                        : ThreatCoverageAnalyzer.DEFAULT_GLOBE_RADIUS_METERS);
                CoverageReport report = analyzer.analyze(scenario.assets, scenario.threats);

                for (AssetExposure exposure : report.getExposures())
                {
                    if (exposure.isExposed())
                        this.addExposureCallout(exposure);
                }

                // Print the coverage summary to the console and show it in the control panel.
                String summary = report.formatSummary();
                System.out.println(summary);
                this.addCoveragePanel(scenario, report, summary);
            }
            catch (Exception e)
            {
                // WorldWind examples must not crash on startup. Log and continue with whatever loaded.
                Logging.logger().log(Level.SEVERE,
                    Logging.getMessage("generic.ExceptionAttemptingToReadFile", SCENARIO_RESOURCE), e);
            }

            insertBeforePlacenames(this.getWwd(), this.threatVolumeLayer);
            insertBeforePlacenames(this.getWwd(), this.symbolLayer);
            insertBeforePlacenames(this.getWwd(), this.statusLayer);

            // Enable click-and-drag repositioning of symbols.
            this.getWwd().addSelectListener(new BasicDragger(this.getWwd()));

            Dimension size = new Dimension(1600, 1000);
            this.setPreferredSize(size);
            this.pack();
            WWUtil.alignComponent(null, this, AVKey.CENTER);
        }

        /**
         * Adds a MIL-STD-2525 friendly symbol for an asset to the symbol layer.
         *
         * @param asset the asset to render.
         */
        protected void addAssetSymbol(Asset asset)
        {
            TacticalSymbol symbol = new MilStd2525TacticalSymbol(sidcForAsset(asset.getType()),
                new Position(asset.getLocation(), 0));
            symbol.setValue(AVKey.DISPLAY_NAME, asset.getName() + " (" + asset.getType() + ")");
            symbol.setAttributes(this.sharedSymbolAttrs);
            symbol.setHighlightAttributes(this.sharedHighlightAttrs);
            symbol.setModifier(SymbologyConstants.UNIQUE_DESIGNATION, asset.getName());
            symbol.setShowLocation(false);
            this.symbolLayer.addRenderable(symbol);
        }

        /**
         * Adds a MIL-STD-2525 hostile air-defense symbol for a threat to the symbol layer.
         *
         * @param threat the threat to render.
         */
        protected void addThreatSymbol(Threat threat)
        {
            TacticalSymbol symbol = new MilStd2525TacticalSymbol(sidcForThreat(threat.getType()),
                new Position(threat.getLocation(), 0));
            double rangeKm = threat.getRadiusMeters() / 1000.0;
            symbol.setValue(AVKey.DISPLAY_NAME,
                String.format("%s - WEZ %.0f km", threat.getName(), rangeKm));
            symbol.setAttributes(this.sharedSymbolAttrs);
            symbol.setHighlightAttributes(this.sharedHighlightAttrs);
            symbol.setModifier(SymbologyConstants.UNIQUE_DESIGNATION, threat.getName());

            // Distinguish long/medium/short-range systems on the map with a MIL-STD-2525 echelon marker.
            String echelon = echelonForThreat(threat.getType());
            if (echelon != null)
                symbol.setModifier(SymbologyConstants.ECHELON, echelon);

            symbol.setShowLocation(false);
            this.symbolLayer.addRenderable(symbol);
        }

        /**
         * Adds a translucent, hostile-red weapon-engagement-zone volume for a threat to the threat layer.
         *
         * @param threat the threat whose weapon-engagement zone is drawn.
         */
        protected void addThreatVolume(Threat threat)
        {
            CappedCylinder wez = new CappedCylinder(threat.getLocation(), threat.getRadiusMeters());
            wez.setAltitudes(WEZ_VOLUME_FLOOR_METERS, WEZ_VOLUME_CEILING_METERS);
            wez.setTerrainConforming(true, false);
            wez.setValue(AVKey.DISPLAY_NAME,
                String.format("%s weapon-engagement zone", threat.getName()));

            AirspaceAttributes attrs = new BasicAirspaceAttributes();
            attrs.setDrawInterior(true);
            attrs.setDrawOutline(true);
            attrs.setInteriorMaterial(Material.RED);
            attrs.setInteriorOpacity(0.12);
            attrs.setOutlineMaterial(Material.RED);
            attrs.setOutlineOpacity(0.8);
            attrs.setOutlineWidth(2.0);
            wez.setAttributes(attrs);

            this.threatVolumeLayer.addRenderable(wez);
        }

        /**
         * Adds a red exposure callout for an exposed asset to the status layer.
         *
         * @param exposure the exposure result to annotate.
         */
        protected void addExposureCallout(AssetExposure exposure)
        {
            Asset asset = exposure.getAsset();

            StringBuilder threatNames = new StringBuilder();
            for (Threat threat : exposure.getExposingThreats())
            {
                if (threatNames.length() > 0)
                    threatNames.append(", ");
                threatNames.append(threat.getName());
            }

            String text = String.format("EXPOSED: %s\nby %s", asset.getName(), threatNames.toString());
            GlobeAnnotation callout = new GlobeAnnotation(text, new Position(asset.getLocation(), 0));

            AnnotationAttributes attrs = callout.getAttributes();
            attrs.setTextColor(Color.WHITE);
            attrs.setBackgroundColor(new Color(160, 0, 0, 200));
            attrs.setBorderColor(Color.RED);
            attrs.setFont(Font.decode("Arial-BOLD-12"));
            attrs.setInsets(new Insets(6, 6, 6, 6));
            attrs.setDrawOffset(new Point(0, 40));

            this.statusLayer.addRenderable(callout);
        }

        /**
         * Adds a control-panel readout of the coverage summary.
         *
         * @param scenario the loaded scenario.
         * @param report   the computed coverage report.
         * @param summary  the formatted summary text.
         */
        protected void addCoveragePanel(Scenario scenario, CoverageReport report, String summary)
        {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(new CompoundBorder(new EmptyBorder(10, 10, 10, 10),
                new TitledBorder("Protection Coverage")));

            JLabel headline = new JLabel(String.format("<html><b>%d of %d assets EXPOSED</b><br>%d threats tracked</html>",
                report.getExposedCount(), report.getTotalCount(), scenario.threats.size()));
            panel.add(headline, BorderLayout.NORTH);

            JTextArea textArea = new JTextArea(summary);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(340, 320));
            panel.add(scrollPane, BorderLayout.CENTER);

            this.getControlPanel().add(panel, BorderLayout.SOUTH);
        }
    }

    /** Container for a loaded scenario. */
    protected static class Scenario
    {
        protected final List<Asset> assets = new ArrayList<Asset>();
        protected final List<Threat> threats = new ArrayList<Threat>();
    }

    /**
     * Loads the bundled scenario resource into a {@link Scenario}.
     *
     * @return the parsed scenario.
     *
     * @throws IOException if the resource cannot be read.
     */
    protected static Scenario loadScenario() throws IOException
    {
        Scenario scenario = new Scenario();

        InputStream stream = WWIO.openFileOrResourceStream(SCENARIO_RESOURCE, AirDefenseCoverage.class);
        if (stream == null)
        {
            String message = Logging.getMessage("generic.CannotOpenFile", SCENARIO_RESOURCE);
            Logging.logger().severe(message);
            throw new FileNotFoundException(message);
        }

        // Close the underlying stream in the finally block so it is released even if reader construction fails.
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null)
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;

                parseScenarioLine(line, scenario);
            }
        }
        finally
        {
            WWIO.closeStream(stream, SCENARIO_RESOURCE);
        }

        return scenario;
    }

    /**
     * Parses a single non-comment scenario line and adds the resulting entity to the scenario. A malformed line is
     * logged and skipped so one bad record does not abort loading the rest of the scenario.
     *
     * @param line     the trimmed scenario line.
     * @param scenario the scenario to populate.
     */
    protected static void parseScenarioLine(String line, Scenario scenario)
    {
        String[] fields = line.split(",", -1);
        if (fields.length < 5)
        {
            Logging.logger().warning(Logging.getMessage("generic.CannotParse", line));
            return;
        }

        try
        {
            String kind = fields[0].trim();
            String name = fields[1].trim();
            String type = fields[2].trim();
            double lat = Double.parseDouble(fields[3].trim());
            double lon = Double.parseDouble(fields[4].trim());
            LatLon location = LatLon.fromDegrees(lat, lon);

            if ("ASSET".equalsIgnoreCase(kind))
            {
                scenario.assets.add(new Asset(name, type, location));
            }
            else if ("THREAT".equalsIgnoreCase(kind))
            {
                boolean hasRadius = fields.length > 5 && !fields[5].trim().isEmpty();
                if (!hasRadius)
                    Logging.logger().warning(Logging.getMessage("generic.CannotParse", line));

                double radiusKm = hasRadius ? Double.parseDouble(fields[5].trim()) : 0.0;
                scenario.threats.add(new Threat(name, type, location, radiusKm * 1000.0));
            }
            else
            {
                Logging.logger().warning(Logging.getMessage("generic.CannotParse", line));
            }
        }
        catch (NumberFormatException e)
        {
            // Skip the malformed record but keep loading the remaining scenario entries.
            Logging.logger().warning(Logging.getMessage("generic.CannotParse", line));
        }
    }

    /**
     * Maps a friendly asset type to a MIL-STD-2525 friendly symbol identification code.
     *
     * @param type the asset type.
     *
     * @return a 15-character friendly SIDC.
     */
    protected static String sidcForAsset(String type)
    {
        if (type == null)
            return "SFGPU----------";

        switch (type.toUpperCase())
        {
            case "AIRFIELD":
                return "SFGPIBA--------"; // Friendly aviation installation
            case "MANEUVER":
                return "SFGPUCI--------"; // Friendly maneuver (infantry)
            case "SUSTAINMENT":
                return "SFGPUSS--------"; // Friendly combat service support
            case "C2":
                return "SFGPUH---------"; // Friendly headquarters
            case "AIR_DEFENSE":
                return "SFGPUCD--------"; // Friendly air defense
            default:
                return "SFGPU----------"; // Friendly ground unit
        }
    }

    /**
     * Maps a hostile threat type to a MIL-STD-2525 hostile air-defense symbol identification code.
     *
     * @param type the threat type.
     *
     * @return a 15-character hostile SIDC.
     */
    protected static String sidcForThreat(String type)
    {
        // All modeled threats are hostile air-defense (surface-to-air) systems.
        return "SHGPUCD--------";
    }

    /**
     * Maps a threat type to a MIL-STD-2525 echelon code so long-, medium-, and short-range systems are visually
     * distinguishable on the map even though they share the hostile air-defense icon.
     *
     * @param type the threat type.
     *
     * @return an echelon code from {@link SymbologyConstants}, or {@code null} for no echelon marker.
     */
    protected static String echelonForThreat(String type)
    {
        if (type == null)
            return null;

        switch (type.toUpperCase())
        {
            case "SAM_LONG":
                return SymbologyConstants.ECHELON_BATTALION_SQUADRON;
            case "SAM_MEDIUM":
                return SymbologyConstants.ECHELON_COMPANY_BATTERY_TROOP;
            case "SAM_SHORT":
                return SymbologyConstants.ECHELON_PLATOON_DETACHMENT;
            default:
                return null;
        }
    }

    public static void main(String[] args)
    {
        // Configure the initial view to look at the Baltic area of operations.
        Configuration.setValue(AVKey.INITIAL_LATITUDE, 55.7);
        Configuration.setValue(AVKey.INITIAL_LONGITUDE, 23.5);
        Configuration.setValue(AVKey.INITIAL_ALTITUDE, 1400000);
        Configuration.setValue(AVKey.INITIAL_PITCH, 0);

        start("WorldWind Protection Cell Air-Defense Coverage", AppFrame.class);
    }
}
