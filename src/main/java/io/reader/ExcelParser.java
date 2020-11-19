package io.reader;

import database.ColumnNameMapper;
import io.IInputData;
import io.datastructure.Entry;
import io.datastructure.generic.GenericInputData;
import io.inputtypes.CategoricInputType;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;


/**
 * Created by neukamm on 22.03.17.
 */
public class ExcelParser implements IInputData{

    private HashMap<String, List<Entry>> map = new HashMap<>();

    public ExcelParser(String file, Logger logger, Set<String> message_duplications) throws IOException {

        Logger LOG = logger;
        LOG.info("Read Excel file: " + file);

        ColumnNameMapper mapper = new ColumnNameMapper();
        String excelFilePath = file;
        FileInputStream inputStream = new FileInputStream(new File(excelFilePath));

        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet firstSheet = workbook.getSheetAt(0);
        Iterator<Row> iterator = firstSheet.iterator();

        Iterator<Cell> cellIterator;

        // read header
        List<String> header = new ArrayList<>();
        Row headerRow = iterator.next();

        if(headerRow.getCell(0).getStringCellValue().startsWith("##")){
            cellIterator = headerRow.cellIterator();


            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                switch (cell.getCellType()) {
                    case STRING:
                        header.add(cell.getStringCellValue().replace("##","").trim());
                        break;
                }
            }
        }


        // read types
        List<String> types = new ArrayList<>();
        Row typeRow = iterator.next();

        if(typeRow.getCell(0).getStringCellValue().startsWith("#")){
            cellIterator = typeRow.cellIterator();

            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();

                switch(cell.getCellType()) {
                    case STRING:
                        types.add(cell.getStringCellValue().replace("#", "").trim());
                        break;
                }
            }
        }


        List<Entry> entries;
        while (iterator.hasNext()) {
            Row nextRow = iterator.next();
            cellIterator = nextRow.cellIterator();
            String id = "";
            int i = 0;
            entries = new ArrayList<>();

            Cell cell;

            for(int j = 0; j < nextRow.getLastCellNum(); j++) {
                cell = nextRow.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Entry e = null;
                String colname;
                String data;

                switch (cell.getCellType()) {
                    case STRING:
                        colname = mapper.mapString(header.get(i));
                        data = cell.getStringCellValue();

                        if(colname.equals("ID")){
                            id = cell.getStringCellValue();
                            id = id.split(" ")[0];
                            if(id.matches(".*[^\\d]\\d{1}$")){
                                id = id.split("\\.")[0];
                                data = id;
                            }
                        }
                        e = new Entry(colname, new CategoricInputType("String"), new GenericInputData(data));
                        i++;
                        break;
                    case NUMERIC:
                        colname = mapper.mapString(header.get(i));
                        data = String.valueOf(cell.getNumericCellValue());
                        e = new Entry(colname, new CategoricInputType("String"), new GenericInputData(data));
                        i++;
                        break;
                    case BOOLEAN:
                        colname = mapper.mapString(header.get(i));
                        data = String.valueOf(cell.getBooleanCellValue());
                        e = new Entry(colname, new CategoricInputType("String"), new GenericInputData(data));
                        i++;
                        break;
                    case BLANK:
                        colname = mapper.mapString(header.get(i));
                        data = "";
                        e = new Entry(colname, new CategoricInputType("String"), new GenericInputData(data));
                        i++;
                        break;

                }
                entries.add(e);
            }



            // Duplicates within input file are not allowed!
            if(map.keySet().contains(id)){
                message_duplications.add(id);
//                DuplicatesException duplicatesException = new DuplicatesException("The input file contains duplicates: " + id +
//                        "\nOnly first hit will be added");
//                DuplicatesErrorDialogue duplicatesErrorDialogue = new DuplicatesErrorDialogue(duplicatesException);
            } else {
                map.put(id , entries);
            }

            i++;
        }

        workbook.close();
        inputStream.close();

    }

    @Override
    public HashMap<String, List<Entry>> getCorrespondingData() {
        return this.map;
    }
}