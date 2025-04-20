package org.msv.vt100.OrderAutomation;

public class RowData {
    public final String firm;
    public final String order;
    public final String position;
    public final String modelDescription;
    public final String modelNumber;
    public final String deliveryDate;
    public final String abDate;
    public final boolean isLastLine;

    public RowData(String firm, String order, String position, String modelDescription,
                   String modelNumber, String deliveryDate, String abDate, boolean isLastLine) {
        this.firm = firm;
        this.order = order;
        this.position = position;
        this.modelDescription = modelDescription;
        this.modelNumber = modelNumber;
        this.deliveryDate = deliveryDate;
        this.abDate = abDate;
        this.isLastLine = isLastLine;
    }
}