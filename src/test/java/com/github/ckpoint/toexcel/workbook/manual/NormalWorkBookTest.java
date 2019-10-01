package com.github.ckpoint.toexcel.workbook.manual;

import com.github.ckpoint.toexcel.core.ToWorkBook;
import com.github.ckpoint.toexcel.core.ToWorkSheet;
import com.github.ckpoint.toexcel.core.type.WorkBookType;
import org.junit.Test;

import java.io.IOException;
import java.util.stream.IntStream;

public class NormalWorkBookTest {

    @Test
    public void test_01() throws IOException {
        ToWorkBook workBook = new ToWorkBook(WorkBookType.XSSF);
        ToWorkSheet sheet = workBook.createSheet();

        sheet.createTitleCell(2, "이름", "나이");
        sheet.next();
        IntStream.range(0, 50).forEach(i ->{
            sheet.createCell("희섭" + i , 1 + i);
            sheet.next();
        });

        workBook.writeFile("target/excel/test.xls");

    }
}