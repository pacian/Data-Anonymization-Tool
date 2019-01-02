/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2016 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.gui.view.impl.risk;

import java.util.Arrays;

import org.deidentifier.arx.gui.Controller;
import org.deidentifier.arx.gui.model.ModelEvent;
import org.deidentifier.arx.gui.model.ModelEvent.ModelPart;
import org.deidentifier.arx.gui.model.ModelRisk.ViewRiskType;
import org.deidentifier.arx.gui.resources.Resources;
import org.deidentifier.arx.gui.view.SWTUtil;
import org.deidentifier.arx.gui.view.impl.common.ClipboardHandlerTable;
import org.deidentifier.arx.gui.view.impl.common.ComponentStatusLabelProgressProvider;
import org.deidentifier.arx.gui.view.impl.common.ComponentTitledSeparator;
import org.deidentifier.arx.gui.view.impl.common.async.Analysis;
import org.deidentifier.arx.gui.view.impl.common.async.AnalysisContext;
import org.deidentifier.arx.gui.view.impl.common.async.AnalysisManager;
import org.deidentifier.arx.risk.RiskEstimateBuilderInterruptible;
import org.deidentifier.arx.risk.RiskModelMSU;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.IAxisSet;
import org.swtchart.IBarSeries;
import org.swtchart.ISeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;
import org.swtchart.ITitle;
import org.swtchart.Range;

import de.linearbits.swt.table.DynamicTable;
import de.linearbits.swt.table.DynamicTableColumn;

/**
 * This view displays statistics about MSUs.
 *
 * @author Fabian Prasser
 */
public class ViewRisksMSUs extends ViewRisks<AnalysisContextRisk> {

    /** Label */
    private static final String LABEL_ATTRIBUTE         = Resources.getMessage("RiskAnalysisMSU.1");
    /** Label */
    private static final String LABEL_CONTRIBUTION      = Resources.getMessage("RiskAnalysisMSU.2");
    /** Label */
    private static final String LABEL_AVERAGE_SIZE      = Resources.getMessage("RiskAnalysisMSU.3");
    /** Label */
    private static final String LABEL_SIZE              = Resources.getMessage("RiskAnalysisMSU.4");
    /** Label */
    private static final String LABEL_FRACTION          = Resources.getMessage("RiskAnalysisMSU.5");
    /** Label */
    private static final String LABEL_DISTRIBUTION      = Resources.getMessage("RiskAnalysisMSU.6");
    /** Label */
    private static final String LABEL_COLUMN_PROPERTIES = Resources.getMessage("RiskAnalysisMSU.7");
    /** Label */
    private static final String LABEL_NO_MSUS_FOUND     = Resources.getMessage("RiskAnalysisMSU.8");

    /** Minimal width of a category label. */
    private static final int    MIN_CATEGORY_WIDTH      = 10;

    /** The chart. */
    private Chart               chart;

    /** View */
    private Composite           rootChart;

    /** View */
    private Composite           rootTable;

    /** View */
    private Composite           root;

    /** View */
    private SashForm            sash;

    /** View */
    private DynamicTable        tableAttributes;

    /** View */
    private Text               numKeys;

    /** View */
    private Text               avgKeySize;

    /** Internal stuff. */
    private AnalysisManager     manager;

    /**
     * Creates a new instance.
     *
     * @param parent
     * @param controller
     * @param target
     * @param reset
     */
    public ViewRisksMSUs(final Composite parent,
                         final Controller controller,
                         final ModelPart target,
                         final ModelPart reset) {
        
        super(parent, controller, target, reset);
        this.manager = new AnalysisManager(parent.getDisplay());
        controller.addListener(ModelPart.ATTRIBUTE_TYPE, this);
    }
    
    @Override
    public void update(ModelEvent event) {
        super.update(event);
        if (event.part == ModelPart.ATTRIBUTE_TYPE) {
            triggerUpdate();
        }
    }
    
    /**
     * Clears the given table
     * @param table
     */
    private void clearTable(DynamicTable table) {
        if (table == null) {
            return;
        }
        for( TableItem i : table.getItems()) {
            i.dispose();
        }
    }

    /**
     * Creates a new table
     * @param root
     * @param title
     * @return
     */
    private DynamicTable createTable(Composite root, String[] columns, String[] bars) {
        DynamicTable table = SWTUtil.createTableDynamic(root, SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setMenu(new ClipboardHandlerTable(table).getMenu());
        String width = String.valueOf((int) (100d / (double) columns.length)) + "%"; //$NON-NLS-1$
        for (String column : columns) {
            DynamicTableColumn c = new DynamicTableColumn(table, SWT.LEFT);
            if (Arrays.asList(bars).contains(column)) {
                SWTUtil.createColumnWithBarCharts(table, c);
            }
            c.setWidth(width, "100px"); //$NON-NLS-1$
            c.setText(column);
        }
        for (final TableColumn col : table.getColumns()) {
            col.pack();
        }
        table.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        SWTUtil.createGenericTooltip(table);
        return table;
    }

    /**
     * Creates the chart
     * @param parent 
     */
    private Chart createChart(Composite parent) {
        
        final Chart chart = new Chart(parent, SWT.NONE);
        chart.setOrientation(SWT.HORIZONTAL);
        chart.setLayoutData(SWTUtil.createFillGridData());
        
        // Show/Hide axis
        chart.addControlListener(new ControlAdapter(){
            @Override
            public void controlResized(ControlEvent arg0) {
                updateCategories();
            }
        });

        // Update font
        FontData[] fd = chart.getFont().getFontData();
        fd[0].setHeight(8);
        final Font font = new Font(chart.getDisplay(), fd[0]);
        chart.setFont(font);
        chart.addDisposeListener(new DisposeListener(){
            public void widgetDisposed(DisposeEvent arg0) {
                if (font != null && !font.isDisposed()) {
                    font.dispose();
                }
            } 
        });
        
        // Update title
        ITitle graphTitle = chart.getTitle();
        graphTitle.setText(""); //$NON-NLS-1$
        graphTitle.setFont(chart.getFont());
        
        // Set colors
        chart.setBackground(root.getBackground());
        chart.setForeground(root.getForeground());
        
        // OSX workaround
        if (System.getProperty("os.name").toLowerCase().contains("mac")){ //$NON-NLS-1$ //$NON-NLS-2$
            int r = chart.getBackground().getRed()-13;
            int g = chart.getBackground().getGreen()-13;
            int b = chart.getBackground().getBlue()-13;
            r = r>0 ? r : 0;
            r = g>0 ? g : 0;
            r = b>0 ? b : 0;
            final Color background = new Color(chart.getDisplay(), r, g, b);
            chart.setBackground(background);
            chart.addDisposeListener(new DisposeListener(){
                public void widgetDisposed(DisposeEvent arg0) {
                    if (background != null && !background.isDisposed()) {
                        background.dispose();
                    }
                } 
            });
        }

        // Initialize axes
        IAxisSet axisSet = chart.getAxisSet();
        IAxis yAxis = axisSet.getYAxis(0);
        IAxis xAxis = axisSet.getXAxis(0);
        ITitle xAxisTitle = xAxis.getTitle();
        xAxisTitle.setText(""); //$NON-NLS-1$
        xAxis.getTitle().setFont(chart.getFont());
        yAxis.getTitle().setFont(chart.getFont());
        xAxis.getTick().setFont(chart.getFont());
        yAxis.getTick().setFont(chart.getFont());
        xAxis.getTick().setForeground(chart.getForeground());
        yAxis.getTick().setForeground(chart.getForeground());
        xAxis.getTitle().setForeground(chart.getForeground());
        yAxis.getTitle().setForeground(chart.getForeground());

        // Initialize y-axis
        ITitle yAxisTitle = yAxis.getTitle();
        yAxisTitle.setText(LABEL_FRACTION);
        chart.setEnabled(false);

        // Tool tip
        final StringBuilder builder = new StringBuilder();
        rootChart.addListener(SWT.MouseMove, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (chart != null) {
                    IAxisSet axisSet = chart.getAxisSet();
                    if (axisSet != null) {
                        IAxis xAxis = axisSet.getXAxis(0);
                        if (xAxis != null) {
                            Point cursor = chart.getPlotArea().toControl(Display.getCurrent().getCursorLocation());
                            if (cursor.x >= 0 && cursor.x < chart.getPlotArea().getSize().x && 
                                cursor.y >= 0 && cursor.y < chart.getPlotArea().getSize().y) {
                                String[] series = xAxis.getCategorySeries();
                                ISeries[] data = chart.getSeriesSet().getSeries();
                                if (data != null && data.length > 0 && series != null) {
                                    int x = (int) Math.round(xAxis.getDataCoordinate(cursor.x));
                                    if (x >= 0 && x < series.length && !series[x].equals("")) {
                                        builder.setLength(0);
                                        builder.append("("); //$NON-NLS-1$
                                        builder.append(LABEL_SIZE).append(": "); //$NON-NLS-1$
                                        builder.append(SWTUtil.getPrettyString(series[x]));
                                        builder.append(", "); //$NON-NLS-1$
                                        builder.append(LABEL_FRACTION).append(": "); //$NON-NLS-1$
                                        builder.append(SWTUtil.getPrettyString(data[0].getYSeries()[x]));
                                        builder.append(")"); //$NON-NLS-1$
                                        rootChart.setToolTipText(builder.toString());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    root.setToolTipText(null);
                }
            }
        });
        
        // Return
        return chart;
    }

    /**
     * Makes the chart show category labels or not.
     */
    private void updateCategories(){
        if (chart != null){
            IAxisSet axisSet = chart.getAxisSet();
            if (axisSet != null) {
                IAxis xAxis = axisSet.getXAxis(0);
                if (xAxis != null) {
                    String[] series = xAxis.getCategorySeries();
                    if (series != null) {
                        boolean enoughSpace = chart.getPlotArea().getSize().x / series.length >= MIN_CATEGORY_WIDTH;
                        xAxis.enableCategory(enoughSpace);
                        xAxis.getTick().setVisible(enoughSpace);
                    }
                }
            }
        }
    }

    @Override
    protected Control createControl(Composite parent) {

        // Root
        this.root = new Composite(parent, SWT.NONE);
        this.root.setLayout(new FillLayout());
        
        // Sash
        this.sash = new SashForm(this.root, SWT.VERTICAL);

        // Chart
        this.rootChart = new Composite(sash, SWT.NONE);
        this.rootChart.setLayout(SWTUtil.createGridLayout(1));
        ComponentTitledSeparator separator1 = new ComponentTitledSeparator(rootChart, SWT.NONE);
        separator1.setText(LABEL_DISTRIBUTION);
        separator1.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        
        // Stats
        Composite stats = new Composite(rootChart, SWT.NONE);
        stats.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        stats.setLayout(SWTUtil.createGridLayout(4, false));
        Label lbl1 = new Label(stats, SWT.NONE);
        lbl1.setText("Number of keys:");
        lbl1.setLayoutData(SWTUtil.createGridData());
        numKeys = new Text(stats, SWT.BORDER);
        numKeys.setText("");
        numKeys.setLayoutData(SWTUtil.createFillGridData());
        numKeys.setEditable(false);
        Label lbl3 = new Label(stats, SWT.NONE);
        lbl3.setText("Average size:");
        lbl3.setLayoutData(SWTUtil.createGridData());
        avgKeySize = new Text(stats, SWT.BORDER);
        avgKeySize.setText("");
        avgKeySize.setLayoutData(SWTUtil.createFillGridData());
        avgKeySize.setEditable(false);
        
        // Chart
        this.chart = this.createChart(rootChart);
        this.updateCategories();

        // Table
        this.rootTable = new Composite(sash, SWT.NONE);
        this.rootTable.setLayout(SWTUtil.createGridLayout(1));
        ComponentTitledSeparator separator2 = new ComponentTitledSeparator(rootTable, SWT.NONE);
        separator2.setText(LABEL_COLUMN_PROPERTIES);
        separator2.setLayoutData(SWTUtil.createFillHorizontallyGridData());
        this.tableAttributes = createTable(rootTable, new String[]{LABEL_ATTRIBUTE, LABEL_CONTRIBUTION, LABEL_AVERAGE_SIZE}, new String[]{LABEL_CONTRIBUTION});
        this.tableAttributes.setLayoutData(SWTUtil.createFillGridData());
        
        // Configure & return
        sash.setWeights(new int[] {2, 2});
        this.root.layout();
        return this.root;
    }

    @Override
    protected AnalysisContextRisk createViewConfig(AnalysisContext context) {
        return new AnalysisContextRisk(context);
    }

    @Override
    protected void doReset() {
        if (this.manager != null) {
            this.manager.stop();
        }
        root.setRedraw(false);
        if (this.chart.getSeriesSet().getSeries(LABEL_SIZE) != null) {
            this.chart.getSeriesSet().deleteSeries(LABEL_SIZE);
        }
        this.clearTable(tableAttributes);
        numKeys.setText("");
        avgKeySize.setText("");
        numKeys.setToolTipText("");
        avgKeySize.setToolTipText("");
        root.setRedraw(true);
        setStatusEmpty();
    }

    @Override
    protected void doUpdate(final AnalysisContextRisk context) {

        // Enable/disable
        final RiskEstimateBuilderInterruptible builder = getBuilder(context);
        if (!this.isEnabled() || builder == null) {
            if (manager != null) {
                manager.stop();
            }
            this.setStatusEmpty();
            return;
        }

        // Create an analysis
        Analysis analysis = new Analysis() {

            private boolean  stopped = false;
            private double[] msuSizeDistribution;
            private double[] columnContribution;
            private double[] columnAverageKeySize;
            private String[] attributes;
            private long     numKeys;
            private double   avgKeySize;
            
            @Override
            public int getProgress() {
                return builder == null ? 0 : builder.getProgress();
            }

            @Override
            public void onError() {
                setStatusEmpty();
            }

            @Override
            public void onFinish() {

                // Check
                if (stopped || !isEnabled()) {
                    return;
                }

                // Disable redraw
                root.setRedraw(false);
                                
                // Fill chart
                if (msuSizeDistribution.length == 0) {
                    if (chart.getSeriesSet().getSeries(LABEL_SIZE) != null) {
                        chart.getSeriesSet().deleteSeries(LABEL_SIZE);
                    }
                } else {
                    ISeriesSet seriesSet = chart.getSeriesSet();
                    IBarSeries series = (IBarSeries) seriesSet.createSeries(SeriesType.BAR, LABEL_SIZE); //$NON-NLS-1$
                    series.getLabel().setVisible(false);
                    series.getLabel().setFont(chart.getFont());
                    series.setBarColor(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                    String[] labels = new String[msuSizeDistribution.length];
                    for (int i = 0; i < msuSizeDistribution.length; i++) {
                        if (Double.isNaN(msuSizeDistribution[i])) {
                            msuSizeDistribution[i] = 0d;
                        } else {
                            msuSizeDistribution[i] *= 100d;
                        }
                        labels[i] = String.valueOf(i + 1);
                    }
                    series.setYSeries(msuSizeDistribution);
                    
                    // Configure
                    chart.getLegend().setVisible(false);
                    IAxisSet axisSet = chart.getAxisSet();
    
                    // X-axis
                    IAxis yAxis = axisSet.getYAxis(0);
                    yAxis.setRange(new Range(0d, 100d));
    
                    // X-axis
                    IAxis xAxis = axisSet.getXAxis(0);
                    xAxis.setCategorySeries(labels);
                    xAxis.adjustRange();
                    updateCategories();
                    chart.updateLayout();
                    chart.update();
                }

                // Clear table
                clearTable(tableAttributes);

                // Create entries for table
                for (int i=0; i<columnContribution.length; i++) {
                    TableItem item = new TableItem(tableAttributes, SWT.NONE);
                    item.setText(0, attributes[i]);
                    if (Double.isNaN(columnContribution[i])) {
                        item.setText(1, LABEL_NO_MSUS_FOUND);
                    } else {
                        item.setData("1", columnContribution[i]); //$NON-NLS-1$                
                    }
                    if (Double.isNaN(columnAverageKeySize[i])) {
                        item.setText(2, LABEL_NO_MSUS_FOUND);
                    } else {
                        item.setText(2, SWTUtil.getPrettyString(columnAverageKeySize[i]));                    
                    }
                }
                
                // Stats
                ViewRisksMSUs.this.numKeys.setText(numKeys != 0 ? SWTUtil.getPrettyString(numKeys) : LABEL_NO_MSUS_FOUND);
                ViewRisksMSUs.this.numKeys.setToolTipText(numKeys != 0 ? String.valueOf(numKeys) : LABEL_NO_MSUS_FOUND);
                ViewRisksMSUs.this.avgKeySize.setText(numKeys != 0 ? SWTUtil.getPrettyString(avgKeySize) : LABEL_NO_MSUS_FOUND);
                ViewRisksMSUs.this.avgKeySize.setToolTipText(numKeys != 0 ? String.valueOf(avgKeySize) : LABEL_NO_MSUS_FOUND);
                
                // Enable
                root.layout();
                root.setRedraw(true);
                setStatusDone();
            }

            @Override
            public void onInterrupt() {
                if (!isEnabled() || !isValid()) {
                    setStatusEmpty();
                } else {
                    setStatusWorking();
                }
            }

            @Override
            public void run() throws InterruptedException {

                // Timestamp
                long time = System.currentTimeMillis();

                // Perform work
                RiskModelMSU model = builder.getMSUStatistics();

                // Create array
                msuSizeDistribution = model.getKeySizeDistribution();
                columnContribution = model.getColumnKeyContributions();
                columnAverageKeySize = model.getColumnAverageKeySize();
                attributes = model.getAttributes();
                numKeys = model.getNumKeys();
                avgKeySize = model.getAverageKeySize();
              
                // Our users are patient
                while (System.currentTimeMillis() - time < MINIMAL_WORKING_TIME && !stopped) {
                    Thread.sleep(10);
                }
            }

            @Override
            public void stop() {
                if (builder != null) builder.interrupt();
                this.stopped = true;
            }
        };

        this.manager.start(analysis);
    }

    @Override
    protected ComponentStatusLabelProgressProvider getProgressProvider() {
        return new ComponentStatusLabelProgressProvider(){
            public int getProgress() {
                if (manager == null) {
                    return 0;
                } else {
                    return manager.getProgress();
                }
            }
        };
    }
    
    @Override
    protected ViewRiskType getViewType() {
        return ViewRiskType.CLASSES_TABLE;
    }
    
    /**
     * Is an analysis running
     */
    protected boolean isRunning() {
        return manager != null && manager.isRunning();
    }
}
