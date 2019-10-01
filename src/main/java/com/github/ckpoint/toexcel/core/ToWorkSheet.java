package com.github.ckpoint.toexcel.core;

import com.github.ckpoint.toexcel.annotation.ExcelHeader;
import com.github.ckpoint.toexcel.core.model.ToTitleKey;
import com.github.ckpoint.toexcel.core.type.ToWorkCellType;
import com.github.ckpoint.toexcel.util.ExcelHeaderHelper;
import com.github.ckpoint.toexcel.util.ModelMapperGenerator;
import com.github.ckpoint.toexcel.util.TitleRowHelper;
import lombok.Getter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type To work sheet.
 */
public class ToWorkSheet implements ExcelHeaderHelper, TitleRowHelper {

    private final static int DEFAULT_WIDTH_SIZE = 2560;

    @Getter
    private final ToWorkBook workBook;

    @Getter
    private final String name;
    private final Workbook _wb;

    private Sheet _sheet;

    /**
     * Instantiates a new To work sheet.
     *
     * @param toWorkBook the to work book
     * @param wb         the wb
     */
    public ToWorkSheet(ToWorkBook toWorkBook, Workbook wb) {
        this(toWorkBook, wb, null);
    }

    /**
     * Instantiates a new To work sheet.
     *
     * @param toWorkBook the to work book
     * @param wb         the wb
     * @param name       the name
     */
    public ToWorkSheet(ToWorkBook toWorkBook, Workbook wb, String name) {
        this.workBook = toWorkBook;
        this._wb = wb;

        if (name != null && !name.isEmpty()) {
            this.name = name;
            this._sheet = this._wb.createSheet(name);
        } else {
            this._sheet = this._wb.createSheet();
            this.name = this._sheet.getSheetName();
        }


        if (_sheet.getLastRowNum() <= 0) {
            this._sheet.createRow(0);
        }
    }

    /**
     * Create title cell list.
     *
     * @param width  the width
     * @param values the values
     * @return the list
     */
    public List<ToWorkCell> createTitleCell(int width, String... values) {
        if (values == null || values.length < 1) {
            return new ArrayList<>();
        }

        List<ToWorkCell> cells = new ArrayList<>();

        for (String value : values) {
            Cell pcell = getNextCell();
            _sheet.setColumnWidth(pcell.getColumnIndex(), DEFAULT_WIDTH_SIZE * width);
            cells.add(new ToWorkCell(this, pcell, value, ToWorkCellType.TITLE));
        }
        return cells;
    }

    /**
     * Create cell list.
     *
     * @param values the values
     * @return the list
     */
    public List<ToWorkCell> createCell(Object... values) {
        if (values == null || values.length < 1) {
            return new ArrayList<>();
        }
        return Arrays.stream(values).map(v -> new ToWorkCell(this, getNextCell(), v)).collect(Collectors.toList());
    }

    /**
     * Next row.
     *
     * @return the row
     */
    public Row next() {
        return this._sheet.createRow(this._sheet.getLastRowNum() + 1);
    }

    /**
     * Map list.
     *
     * @param <T>  the type parameter
     * @param type the type
     * @return the list
     */
    public <T> List<T> map(Class<T> type) {
        Row titleRow = findTitleRow(type, this._sheet);

        Map<Integer, String> excelTitleMap = new HashMap<>();
        for (int i = 0; i < titleRow.getLastCellNum(); i++) {
            excelTitleMap.put(i, titleRow.getCell(i).getStringCellValue());
        }

        List<String> existTitles = IntStream.range(0, titleRow.getLastCellNum()).mapToObj(titleRow::getCell)
                .map(Cell::getStringCellValue).collect(Collectors.toList());

        Field[] fields = type.getDeclaredFields();
        Set<ToTitleKey> keyset = new HashSet<>();
        for (Field field : fields) {
            if (field.getAnnotation(ExcelHeader.class) != null) {
                keyset.add(new ToTitleKey(field, existTitles));
            }
        }
        List<Map<String, Object>> proxyMapList = IntStream.range(titleRow.getRowNum() + 1, this._sheet.getLastRowNum())
                .mapToObj(this._sheet::getRow).map(row -> rowToMap(row, excelTitleMap, keyset)).collect(Collectors.toList());

        return proxyMapList.stream().map(map -> ModelMapperGenerator.enableFieldModelMapper().map(map, type)).collect(Collectors.toList());
    }

    /**
     * From.
     *
     * @param list the list
     */
    public void from(List list) {
        if (list == null || list.isEmpty()) {
            return;
        }

        this.clear();

        Object obj = list.get(0);
        Field[] fields = obj.getClass().getDeclaredFields();
        List<ToTitleKey> keys = Arrays.stream(fields).filter(field -> field.getAnnotation(ExcelHeader.class) != null)
                .map(ToTitleKey::new).sorted().collect(Collectors.toList());
        keys.forEach(key -> this.createTitleCell(1, key.getHeader().headerName()));
        for (Object o : list) {
            writeObject(o, keys);
        }
    }

    private void writeObject(Object obj, List<ToTitleKey> keys) {
        this.next();
        keys.forEach(key -> {
            Object value = "";
            try {
                key.getField().setAccessible(true);
                value = key.getField().get(obj);
            } catch (IllegalAccessException e) {
                //TODO
            }
            this.createCell(value);
        });
    }

    private void clear() {
        for (int i = 0; i < this._sheet.getLastRowNum(); i++) {
            this._sheet.removeRow(this._sheet.getRow(i));
        }
        this._sheet.createRow(0);
    }

    private Map<String, Object> rowToMap(Row row, Map<Integer, String> titleMaps, Set<ToTitleKey> setKeys) {

        Map<String, Object> valueMap = new HashMap<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Object value = getCellValue(row.getCell(i));
            String title = titleMaps.get(i);
            if (title == null) {
                break;
            }
            ToTitleKey toTitleKey = setKeys.stream().filter(key -> key.isMyName(title)).findFirst().orElse(null);
            if (toTitleKey == null) {
                continue;
            }

            valueMap.put(toTitleKey.getKey(), value);
        }
        return valueMap;
    }

    private Object getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case Cell.CELL_TYPE_NUMERIC:
                return cell.getNumericCellValue();
            case Cell.CELL_TYPE_BOOLEAN:
                return cell.getBooleanCellValue();
            default:
                return cell.getStringCellValue();
        }
    }


    private Cell getNextCell() {
        int cellCnt = this.getCellCnt();
        Cell cell = this.getLastRow().createCell(cellCnt);
        return cell;
    }

    private int getCellCnt() {
        int cellCnt = this.getLastRow().getLastCellNum();
        return cellCnt < 0 ? 0 : cellCnt;
    }


    private Row getLastRow() {
        return this._sheet.getRow(this._sheet.getLastRowNum());
    }
}