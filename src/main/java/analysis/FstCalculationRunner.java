package analysis;


import IO.reader.DistanceTypeParser;
import IO.writer.Writer;
import fst.FstHudson1992;
import fst.Linearization;
import javafx.collections.ObservableList;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;

import javafx.scene.text.Text;
import methods.Filter;
import org.apache.log4j.Logger;
import view.MitoBenchWindow;
import view.visualizations.HeatMap;
import view.table.MTStorage;
import controller.TableControllerFstValues;
import controller.TableControllerUserBench;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by neukamm on 20.04.17.
 */
public class FstCalculationRunner {

    private MitoBenchWindow mitobenchWindow;
    private String distance_type;
    private double gamma_a;
    private char missing_data_character;
    private HashMap<String, List<String>> data;
    private TableControllerFstValues tableControllerFstValues;
    private double[][] fsts;
    private double[][] fsts_slatkin=null;
    private double[][] fsts_reynolds=null;
    private String[] groupnames;
    private List<Integer> usableLoci;
    private Writer writer;
    private Logger LOG;


    public FstCalculationRunner(MitoBenchWindow mito, String type, double gamma, char mdc)
            throws IOException {

        mitobenchWindow = mito;
        distance_type = type;
        gamma_a = gamma;
        missing_data_character = mdc;
        LOG = mito.getLogClass().getLogger(this.getClass());

        prepareData(
                mito.getTableControllerUserBench(),
                mito.getTableControllerUserBench().getDataTable().getMtStorage()
        );
    }


    /**
     * This method extract the sample ID and the corresponding mt sequence from the current table view
     * and stores them in groups defined by the data grouping.
     *
     * @param tableControllerUserBench
     * @param mtStorage
     */
    private void prepareData(TableControllerUserBench tableControllerUserBench, MTStorage mtStorage){
        data = new HashMap<>();
        int colIndexGrouping = tableControllerUserBench.getColIndex(mitobenchWindow.getGroupController().getColname_group());
        int colIndexSequence = tableControllerUserBench.getColIndex("ID");

        ObservableList<ObservableList> table = tableControllerUserBench.getTable().getItems();
        for(ObservableList row : table){
            String group = (String)row.get(colIndexGrouping);
            String id = (String)row.get(colIndexSequence);
            String sequence = mtStorage.getData().get(id);
            if(!group.equals("Undefined")){
                if(!data.containsKey(group)){
                    List<String> sequences = new ArrayList<>();
                    sequences.add(sequence);
                    data.put(group, sequences);
                } else {
                    List<String> tmp = data.get(group);
                    tmp.add(sequence);
                    data.put(group, tmp);
                }
            }
        }
    }

    /**
     * This method calls the Fst calculator with the already prepared data as input.
     * It also defines if linearization (slatkin / reynolds) has to be done.
     *
     * @param runSlatkin
     * @param runReynolds
     * @param field_level_missing_data
     * @throws IOException
     */
    public void run(boolean runSlatkin, boolean runReynolds, String field_level_missing_data) throws IOException {
        DistanceTypeParser distanceTypeParser = new DistanceTypeParser();
        Filter filter = new Filter();
        Linearization linearization = new Linearization();

        usableLoci = filter.getUsableLoci(
                data,
                missing_data_character,
                Double.parseDouble(field_level_missing_data)
        );


        // calculate Fst with equation introduced by Hudson et al. (1992)
        FstHudson1992 fstHudson1992 = new FstHudson1992(usableLoci);
        fstHudson1992.setDistanceParameter(distanceTypeParser.parse(distance_type), gamma_a);
        fstHudson1992.setData(data);

        fsts = fstHudson1992.calculateFst();
        groupnames = fstHudson1992.getGroupnames();


        // write to file
        writer = new Writer();
        writer.writeResultsFstToString(
                fsts,
                groupnames,
                usableLoci,
                Double.parseDouble(field_level_missing_data)
        );

        writer.addDistanceMatrixToResult(fstHudson1992.getDistanceCalculator().getDistancematrix_d());

        if(runSlatkin){
            fsts_slatkin = linearization.linearizeWithSlatkin(fsts);
            writer.addLinerarizedFstMatrix(fsts_slatkin, "Slatkin's linearized Fsts");
        }
        if(runReynolds){
            fsts_reynolds = linearization.linearizeWithReynolds(fsts);
            writer.addLinerarizedFstMatrix(fsts_reynolds, "Reynolds' distance");
        }

        // init table controller
        tableControllerFstValues = new TableControllerFstValues(mitobenchWindow.getLogClass());
        tableControllerFstValues.init();

        writeLog(runSlatkin, runReynolds, field_level_missing_data);

    }

    /**
     * This method reports the Fst calculation into the LOG file.
     *
     * @param runSlatkin
     * @param runReynolds
     * @param level_missing_data
     */
    private void writeLog(boolean runSlatkin, boolean runReynolds, String level_missing_data) {
        LOG.getLogger(this.getClass()).info("Calculate pairwise Fst " +
                "values between following groups:\n" + Arrays.toString(groupnames));

        LOG.info("\nRun Slatkin: " + runSlatkin + ".\nRun Reynolds: " + runReynolds +
        ".\nLevel of missing data: " + level_missing_data + ".\nMissing data character: " + missing_data_character +
        ".\nGamma a value: " + gamma_a);

    }


    /**
     * This method writes the result to the statistics pane of the mitoBench.
     */
    public void writeResultToMitoBench() {

        String id = "Fst values";
        String tab_header = "fst_values";

        ScrollPane scrollpane_result = new ScrollPane();
        String text = writer.getResult_as_string();
        Text t = new Text();
        t.setText(text);
        t.wrappingWidthProperty().bind(mitobenchWindow.getScene().widthProperty());
        scrollpane_result.setContent(t);

        Tab tab = new Tab();
        tab.setId("tab_" + id);
        tab.setText(tab_header);
        tab.setContent(scrollpane_result);

        mitobenchWindow.getTabpane_statistics().getTabs().add(tab);

    }


    /**
     * This method visualizes the Fst values as heatmap and adds the heatmap to the
     * visualization pane of the mitoBench.
     */

    public void visualizeResult() {

        HeatMap heatMap = new HeatMap("","", mitobenchWindow.getLogClass());
        heatMap.setContextMenu(mitobenchWindow.getTabpane_visualization());
        heatMap.createHeatMap(fsts, groupnames, "");

        Tab tab = new Tab("Fst values");
        tab.setId("tab_heatmap");
        tab.setContent(heatMap.getHeatMap());

        mitobenchWindow.getTabpane_visualization().getTabs().add(tab);

    }


    /**
     * This method writes the results if the Fst calculation to a text file.
     *
     * @param path
     * @throws IOException
     */
    public void writeToFile(String path) throws IOException {
        writer.writeResultsToFile(path+ File.separator+"mitoBench_results_fst.txt");
    }


    /*
            GETTER AND SETTER
     */


    public MitoBenchWindow getMitobenchWindow() {
        return mitobenchWindow;
    }

    public void setMitobenchWindow(MitoBenchWindow mitobenchWindow) {
        this.mitobenchWindow = mitobenchWindow;
    }

    public String getDistance_type() {
        return distance_type;
    }

    public void setDistance_type(String distance_type) {
        this.distance_type = distance_type;
    }

    public double getGamma_a() {
        return gamma_a;
    }

    public void setGamma_a(double gamma_a) {
        this.gamma_a = gamma_a;
    }

    public char getMissing_data_character() {
        return missing_data_character;
    }

    public void setMissing_data_character(char missing_data_character) {
        this.missing_data_character = missing_data_character;
    }

    public HashMap<String, List<String>> getData() {
        return data;
    }

    public void setData(HashMap<String, List<String>> data) {
        this.data = data;
    }

    public TableControllerFstValues getTableControllerFstValues() {
        return tableControllerFstValues;
    }

    public void setTableControllerFstValues(TableControllerFstValues tableControllerFstValues) {
        this.tableControllerFstValues = tableControllerFstValues;
    }

    public double[][] getFsts() {
        return fsts;
    }

    public void setFsts(double[][] fsts) {
        this.fsts = fsts;
    }

    public double[][] getFsts_slatkin() {
        return fsts_slatkin;
    }

    public void setFsts_slatkin(double[][] fsts_slatkin) {
        this.fsts_slatkin = fsts_slatkin;
    }

    public double[][] getFsts_reynolds() {
        return fsts_reynolds;
    }

    public void setFsts_reynolds(double[][] fsts_reynolds) {
        this.fsts_reynolds = fsts_reynolds;
    }

    public String[] getGroupnames() {
        return groupnames;
    }

    public void setGroupnames(String[] groupnames) {
        this.groupnames = groupnames;
    }

    public List<Integer> getUsableLoci() {
        return usableLoci;
    }

    public void setUsableLoci(List<Integer> usableLoci) {
        this.usableLoci = usableLoci;
    }

    public Writer getWriter() {
        return writer;
    }

    public void setWriter(Writer writer) {
        this.writer = writer;
    }

    public Logger getLOG() {
        return LOG;
    }

    public void setLOG(Logger LOG) {
        this.LOG = LOG;
    }
}

