package view.visualizations;

import Logging.LogClass;
import controller.ChartController;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import statistics.HaploStatistics;
import controller.TableControllerUserBench;
import view.MitoBenchWindow;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by neukamm on 26.01.17.
 */
public class ProfilePlot extends AChart {

    private final int id;
    private final TabPane tabpaneViz;
    private final MitoBenchWindow mito;
    LineChart<String,Number> profilePlot = new LineChart<String,Number>(xAxis,yAxis);
    List<XYChart.Series> seriesList = new ArrayList<>();
    int maxVal = 0;
    private HaploStatistics haploStatistics;
    //private boolean bContained=false;


    public ProfilePlot(String title, String lable_xaxis, String label_yaxis, TabPane tabpane, LogClass logClass,
                       int uniqueID, MitoBenchWindow mitoBenchWindow){
        super(lable_xaxis, label_yaxis, logClass);

        this.mito = mitoBenchWindow;

        yAxis.setLowerBound(0);
        yAxis.setUpperBound(100);

        profilePlot.setTitle(title);

        id = uniqueID;
        tabpaneViz = tabpane;
        setContextMenu(profilePlot, tabpaneViz);
    }


    /**
     * Create profile plot.
     *
     * @param tableController
     * @param chartController
     * @param logClass
     * @param statsTabpane
     */
    public void create(TableControllerUserBench tableController,
                       ChartController chartController, LogClass logClass, TabPane statsTabpane, String[] hg_list){

        HashMap<String, List<XYChart.Data<String, Number>>> data_all;
        String[] selection_groups;
        int[] number_of_elements;

        if(!tableController.getGroupController().groupingExists()){
            String[][] cols = chartController.prepareColumns(new String[]{"Haplogroup"}, tableController.getSelectedRows());
            String[] selection_haplogroups = cols[0];
            selection_groups = new String[]{"All data"};

            HashMap<String, ArrayList> hgs_summed = chartController.summarizeHaplogroups(Arrays.asList(selection_haplogroups), hg_list);

            data_all = chartController.assignHGsNoGrouping(hgs_summed, Arrays.asList(selection_haplogroups));
            number_of_elements = new int[]{selection_haplogroups.length};

        } else {
            String[][] cols = chartController.prepareColumns(new String[]{"Haplogroup", "Grouping"}, tableController.getSelectedRows());
            String[] selection_haplogroups = cols[0];
            selection_groups = remove(cols[1]);

            HashMap<String, ArrayList> hgs_summed = chartController.summarizeHaplogroups(Arrays.asList(selection_haplogroups), hg_list);
            data_all = chartController.assignHGs(hgs_summed, selection_haplogroups, selection_groups);

            number_of_elements = chartController.getNumberOfElementsPerCategory(selection_groups);
        }

        // sort list alphabetically
        List<String> hg_core_curr = Arrays.asList(hg_list);
        java.util.Collections.sort(hg_core_curr);

        HashMap<String, List<XYChart.Data<String, Number>>> group_hg = new HashMap<>();
        for(String key : hg_core_curr) {

            if (data_all.containsKey(key)) {
                for (int i = 0; i < selection_groups.length; i++) {
                    String group = data_all.get(key).get(i).getXValue();

                    if (!group_hg.containsKey(group)) {
                        List<XYChart.Data<String, Number>> hg = new ArrayList<>();
                        hg.add(data_all.get(key).get(i));
                        group_hg.put(group, hg);
                    } else {
                        List<XYChart.Data<String, Number>> hg_tmp = new ArrayList<>();
                        hg_tmp.addAll(group_hg.get(group));
                        hg_tmp.add(data_all.get(key).get(i));
                        group_hg.put(group, hg_tmp);
                    }

                    double val = data_all.get(key).get(i).getYValue().doubleValue();
                    double val_norm = (val / number_of_elements[i]) * 100;
                    data_all.get(key).get(i).setYValue(chartController.roundValue(val_norm));
                }
            }
        }

        for(String group : group_hg.keySet()){
            addSeries(hg_core_curr, group_hg.get(group), group);
        }

        for(XYChart.Series series : seriesList)
            profilePlot.getData().add(series);

        addListener();
        //setMaxBoundary();
        yAxis.setUpperBound(100);
        xAxis.setTickLabelRotation(0);

        List<String> finalHg_core_curr = hg_core_curr;
        Task task = new Task() {
            @Override
            protected Object call()  {

                haploStatistics = new HaploStatistics(tableController, chartController,logClass);
                haploStatistics.count(finalHg_core_curr.toArray(new String[finalHg_core_curr.size()]));
                return true;
            }
        };

        mito.getProgressBarhandler().activate(task);
        task.setOnCancelled((EventHandler<Event>) event -> {
            mito.getProgressBarhandler().stop();
        });


        task.setOnSucceeded((EventHandler<Event>) event -> {
            TableView table = haploStatistics.writeToTable();
            haploStatistics.addListener(table, this);
            Tab tab = new Tab();
            tab.setId("tab_table_stats_" + id);
            tab.setText("Count statistics (pp " + id + ")");
            tab.setContent(table);
            statsTabpane.getTabs().add(tab);
            statsTabpane.getSelectionModel().select(tab);
            mito.getProgressBarhandler().stop();

        });
        new Thread(task).start();
    }


    private String[] remove(String[] col) {

        List<String> list = new ArrayList<>();
        for(String g : col){
            if(!g.equals(""))
                list.add(g);
        }

        return list.toArray(new String[list.size()]);

    }

    /**
     * This method add all previously creates series to chart.
     *
     * @param hgs
     * @param data
     * @param name
     */
    public void addSeries(List<String> hgs, List<XYChart.Data<String, Number>> data, String name){
        XYChart.Series<String, Number> series = new XYChart.Series<String, Number>();
        series.setName(name);

        for(int i = 0; i < hgs.size(); i++){
            if(data.size() > i){
                double val = data.get(i).getYValue().doubleValue();
                series.getData().add(new XYChart.Data(hgs.get(i), val));
                if(data.get(i).getYValue().doubleValue() > maxVal)
                    maxVal = (int)data.get(i).getYValue().doubleValue();
            }
        }

        this.seriesList.add(series);
    }



    /**
     * This method adds a css file to the profile plot to set the line with to 2px.
     */
    public void addListener(){

        URL url = this.getClass().getResource("/css/ProfilePlot.css");
        profilePlot.getStylesheets().add(url.toExternalForm());
        profilePlot.setCreateSymbols(false);
        profilePlot.getStyleClass().add("thick-chart");

    }



    /*
            Getter and Setter
     */

    public LineChart<String,Number>  getPlot(){
        return profilePlot;
    }


    @Override
    protected void layoutChartChildren(double v, double v1, double v2, double v3) {

    }
}
