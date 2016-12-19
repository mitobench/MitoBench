package view.menus;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import view.charts.BarPlotHaplo;
import view.charts.ColorScheme;
import view.charts.StackedBar;
import view.charts.SunburstChart;
import view.table.TableController;
import view.tree.TreeHaploController;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Created by neukamm on 23.11.16.
 */
public class GraphicsMenu {


    private Menu menuGraphics;
    private TableController tableController;
    private BarPlotHaplo barPlotHaplo;
    private StackedBar stackedBar;
    private SunburstChart sunburstChart;
    private HashMap<String, HashMap<String, Integer>> weights;
    private TabPane tabPane;
    private HashMap<String, List<String>> treeMap;
    private HashMap<String, List<String>> treeMap_path_to_root;
    private TreeItem<String> tree_root;
    private TreeView treeView;
    private Stage stage;


    public GraphicsMenu(TableController tableController, TabPane vBox, TreeHaploController treeController, Stage stage){
        menuGraphics = new Menu("Graphics");
        this.tableController = tableController;
        tabPane = vBox;
        treeMap = treeController.getTreeMap();
        treeMap_path_to_root = treeController.getTreeMap_leaf_to_root();
        tree_root = treeController.deepcopy(treeController.getTree().getTree().getRoot());
        treeView = treeController.getTree().getTree();
        this.stage = stage;
        addSubMenus();
    }

    private void initBarchart(){
        this.barPlotHaplo = new BarPlotHaplo("Haplogroup frequency", "Frequency", tabPane, stage);
        Tab tab = new Tab();
        tab.setText("Bar Chart");
        tab.setContent(barPlotHaplo.getBarChart());
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

    }

    private void initStackedBarchart(){
        this.stackedBar = new StackedBar("Haplogroup frequency per group", tabPane, stage);
        Tab tab = new Tab();
        tab.setText("Bar Chart per group");
        tab.setContent(stackedBar.getSbc());
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

    }

    private void initSunburst(){
        sunburstChart = new SunburstChart(new BorderPane(), stage, tabPane);
        Tab tab = new Tab();
        tab.setText("Sunburst Chart");
        tab.setContent(sunburstChart.getBorderPane());
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);


    }

    public void clearCharts(){
        stackedBar = null;
        barPlotHaplo = null;
        tabPane.getTabs().clear();
    }


    private void addSubMenus() {

        Menu barchart = new Menu("Barchart");


        /*
                        Plot HG frequency

         */

        MenuItem plotHGfreq = new MenuItem("Plot haplogroup frequency");
        plotHGfreq.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
                try {

                    if(tableController.getTable().getItems().size() != 0 ){
                        initBarchart();

                        TableColumn haplo_col = tableController.getTableColumnByName("Haplogroup");
                        List<String> columnData = new ArrayList<>();
                        for (Object item : tableController.getTable().getItems()) {
                            columnData.add((String)haplo_col.getCellObservableValue(item).getValue());
                        }
                        String[] seletcion_haplogroups = columnData.toArray(new String[columnData.size()]);

                        barPlotHaplo.clearData();

                        if (seletcion_haplogroups.length !=0) {
                            barPlotHaplo.addData(tableController.getDataHist());

                        }
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        /*

                    Plot Hg frequency for each group

         */

        MenuItem plotHGfreqGroup = new MenuItem("Plot haplogroup frequency per group(StackedBarchart)");

        plotHGfreqGroup.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
                try {
                    if(tableController.getTableColumnByName("Grouping") != null
                            && tableController.getTable().getItems().size()!=0){

                        initStackedBarchart();

                        String[][] cols = prepareColumns(new String[]{"Haplogroup", "Grouping"}, tableController.getSelectedRows());
                        String[] selection_haplogroups = cols[0];
                        String[] selection_groups = cols[1];


                        // reduce haplogroups to maximum size of 20
                        if(selection_haplogroups.length >= 20) {


                            //------------------------------------------------------

                            stackedBar.clearData();
                            stackedBar.setCategories(selection_groups);
                            HashMap<String, ArrayList> hgs_summed = reduceHGs(selection_haplogroups);
                            List< XYChart.Data<String, Number> > data_list = new ArrayList<XYChart.Data<String, Number>>();

                                // count occurrences of all subHGs in each group
                            for(int i = 0; i < selection_groups.length; i++){

                                String group = selection_groups[i];
                                for(String key : hgs_summed.keySet()) {
                                    int count = 0;
                                    ArrayList<String> subHGs = hgs_summed.get(key);

                                    for(String hg : subHGs){
                                        count += tableController.getCountPerHG(hg, group, tableController.getColIndex("Haplogroup"), tableController.getColIndex("Grouping"));
                                    }
                                    XYChart.Data<String, Number> data = new XYChart.Data<String, Number>(group, count);
                                    data_list.add(data);

                                    stackedBar.addSeries(data_list, key);
                                }


                            }

                            stackedBar.getSbc().getData().addAll(stackedBar.getSeriesList());
                            stackedBar.addTooltip(t);

                            ColorScheme colorScheme = new ColorScheme(stage);
                            colorScheme.setNewColors(stackedBar, selection_haplogroups);






                        } else {
                            stackedBar.clearData();
                            stackedBar.setCategories(selection_groups);

                            // consider Hgs only once per group
                            if (selection_haplogroups.length != 0) {
                                for(int i = 0; i < selection_haplogroups.length; i++){

                                    List< XYChart.Data<String, Number> > data_list = new ArrayList<XYChart.Data<String, Number>>();
                                    // fill data_list : <group(i), countHG >
                                    for(int j = 0; j < selection_groups.length; j++){

                                        int count_per_HG = tableController.getCountPerHG(selection_haplogroups[i], selection_groups[j], tableController.getColIndex("Haplogroup"),
                                                tableController.getColIndex("Grouping"));

                                        XYChart.Data<String, Number> data = new XYChart.Data<String, Number>(selection_groups[j], count_per_HG);
                                        data_list.add(data);
                                    }
                                    stackedBar.addSeries(data_list, selection_haplogroups[i]);
                                }
                            }

                        }


                         stackedBar.getSbc().getData().addAll(stackedBar.getSeriesList());
                        stackedBar.addTooltip(t);

                        ColorScheme colorScheme = new ColorScheme(stage);
                        colorScheme.setNewColors(stackedBar, selection_haplogroups);


                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });



         /*

                    Plot HG frequency for each group

         */

        MenuItem sunburstChartItem = new MenuItem("Create Sunburst chart");
        sunburstChartItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
                try {

                    // makes only sense if grouping exists.
                    if(tableController.getTableColumnByName("Grouping") != null
                            && tableController.getTable().getItems().size() != 0 ){
                        initSunburst();
                        // get selected rows
                        ObservableList<ObservableList> selectedTableItems = tableController.getSelectedRows();
                        HashMap<String, List<String>> hg_to_group = getHG_to_group(selectedTableItems);

                        sunburstChart.create(hg_to_group, weights, treeMap_path_to_root, tree_root, treeView);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

         /*

                    Plot HG frequency for each group

         */

        MenuItem clearPlotBox = new MenuItem("Clear barcharts");
        clearPlotBox.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent t) {
                try {
                    clearCharts();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });


        barchart.getItems().addAll(plotHGfreq, plotHGfreqGroup);

        menuGraphics.getItems().addAll(barchart, sunburstChartItem, new SeparatorMenuItem(), clearPlotBox);
    }



    private HashMap<String, ArrayList> reduceHGs(String[] hgs){

        HashMap<String, ArrayList> hgs_summarized = new HashMap<>();

        String[] hg_reduced = new String[20];
        // number of subHGs
        //"L4-16", "M1-44", "T1-51", "W-59",  "I-62", "X-88", "L1-99", "L0-156", "L2-131", "T2-175",  "K-197" ,  "T-229" ,  "J-239" ,
        //"H-677",  "U-730" , R0-1171",  "HV-1132", "R-2954", "N-3470",    "L3-5010",

        String[] hg_core_list = new String[]{"L4", "M1", "T1", "W", "I", "X",  "L1", "L0", "L2", "T2",
                                             "K",  "T",  "J",  "H", "U", "R0", "HV", "R",  "N",  "L3"};

        for(String hg_core : hg_core_list){
            List<String> core_subs = treeMap.get(hg_core);
            for(String hg : hgs){
                if(core_subs.contains(hg)){
                    if(hgs_summarized.containsKey(hg_core)){
                        ArrayList tmp = hgs_summarized.get(hg_core);
                        tmp.add(hg);
                        hgs_summarized.put(hg_core, tmp);
                    } else {
                        ArrayList<String> tmp = new ArrayList<>();
                        tmp.add(hg);
                        hgs_summarized.put(hg_core, tmp);
                    }
                }
            }
        }

        return hgs_summarized;

    }




    public Menu getMenuGraphics() {
        return menuGraphics;
    }


    /**
     * This method assigns to each group the haplogroups which occurs within this group
     * @return
     */
    public HashMap<String, List<String>> getHG_to_group(ObservableList<ObservableList> selectedTableItems ){



            String[][] cols = prepareColumns(new String[]{"Haplogroup", "Grouping"}, tableController.getSelectedRows());
            String[] seletcion_haplogroups = cols[0];
            String[] seletcion_groups = cols[1];


            // parse selection to tablefilter
            HashMap<String, List<String>> hg_to_group = new HashMap<>();


            if (seletcion_haplogroups.length != 0) {

                // iteration over grouping
                for(int i = 0; i < seletcion_groups.length; i++){
                    String group = seletcion_groups[i];

                    // create new hash entry for each group
                    if(!hg_to_group.containsKey(group)){
                        hg_to_group.put(group, new ArrayList<>());
                    }

                    // iterate over all table view rows
                    for(int k = 0; k < selectedTableItems.size(); k++){
                        ObservableList list = selectedTableItems.get(k);

                        if(list.get( tableController.getColIndex("Grouping")).equals(group)){

                            List<String> tmp = hg_to_group.get(group);
                            tmp.add((String)list.get(tableController.getColIndex("Haplogroup")));
                            hg_to_group.put(group, tmp);

                        }

                    }
                }
            }
            getWeights(seletcion_haplogroups, seletcion_groups);
            return hg_to_group;



    }


    /**
     * This method iterates over groups and their corresponding haplogroups and counts the occurrences per haplogroup
     * per group. Counts are later used as weights for sunburst chart
     *
     * @param seletcion_haplogroups unique list of haplogroups
     * @param seletcion_groups  unique list of groups
     */
    public void getWeights(String[] seletcion_haplogroups, String[] seletcion_groups){

        // hash map
        // Group : <HG : count>
        weights = new HashMap<>();
        Set<String> haplogroups = new HashSet<String>(Arrays.asList(seletcion_haplogroups));

        // get weights
        for(int i = 0; i < seletcion_groups.length; i++) {
            String group = seletcion_groups[i];
            if (!weights.containsKey(group)) {
                weights.put(group, new HashMap<String, Integer>());
            }
            HashMap<String, Integer> hash_tmp = weights.get(group);

            for(String hg : haplogroups){

                // get number of occurrences of this hg within this group
                int count_per_HG = tableController.getCountPerHG(
                        hg,
                        group,
                        tableController.getColIndex("Haplogroup"),
                        tableController.getColIndex("Grouping")
                );

                if(count_per_HG!=0){
                    if (!hash_tmp.containsKey(hg)) {
                        hash_tmp.put(hg, count_per_HG);
                    } else {
                        hash_tmp.put(hg, hash_tmp.get(hg) + 1);
                    }
                }

            }

        }
    }


    /**
     * This method gets all entries of column "Haplogroups" and "Grouping" as set of unique entries.
     *
     * @param names
     * @param selectedTableItems
     * @return
     */
    public String[][] prepareColumns(String[] names, ObservableList<ObservableList> selectedTableItems){


        TableColumn haplo_col = tableController.getTableColumnByName(names[0]);
        TableColumn grouping_col = tableController.getTableColumnByName(names[1]);


        Set<String> columnDataHG = new HashSet<>();
        selectedTableItems.stream().forEach((o)
                -> columnDataHG.add((String)haplo_col.getCellData(o)));

        Set<String> columnDataGroup = new HashSet<>();
        selectedTableItems.stream().forEach((o)
                -> columnDataGroup.add((String)grouping_col.getCellData(o)));

        return new String[][]{columnDataHG.toArray(new String[columnDataHG.size()]),
                              columnDataGroup.toArray(new String[columnDataGroup.size()])};
    }

}
