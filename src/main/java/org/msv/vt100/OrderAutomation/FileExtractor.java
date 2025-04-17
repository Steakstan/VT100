package org.msv.vt100.OrderAutomation;

import org.apache.poi.ss.usermodel.*;

public class FileExtractor {

    public static String extractCellValueAsString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double val = cell.getNumericCellValue();
                        return (val == Math.floor(val)) ? String.valueOf((int) val) : String.valueOf(val);
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue()).trim();
                case FORMULA:
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue evaluated = evaluator.evaluate(cell);
                    return switch (evaluated.getCellType()) {
                        case STRING -> evaluated.getStringValue().trim();
                        case NUMERIC -> (evaluated.getNumberValue() == Math.floor(evaluated.getNumberValue()))
                                ? String.valueOf((int) evaluated.getNumberValue())
                                : String.valueOf(evaluated.getNumberValue());
                        case BOOLEAN -> String.valueOf(evaluated.getBooleanValue()).trim();
                        default -> "";
                    };
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }


}