package tests;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class ExcelReader {

    public static class ClientData {
        public String website;
        public String companyName;
        public String phone;    // multiple numbers separated by ;
        public String address;  // multiple addresses separated by ;
        public String hours;
    }

    public static List<ClientData> getClientData(String filePath) {
        List<ClientData> clientList = new ArrayList<>();

        try (FileReader reader = new FileReader(filePath);
             CSVParser csvParser = new CSVParser(reader,
                     CSVFormat.Builder.create()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreEmptyLines(true)
                             .build())) {

            for (CSVRecord record : csvParser) {
                try {
                    ClientData data = new ClientData();
                    data.website = safeGet(record, "Website");
                    data.companyName = safeGet(record, "CompanyName");
                    data.phone = safeGet(record, "Phone");
                    data.address = safeGet(record, "Address");
                    data.hours = record.isMapped("Hours") ? safeGet(record, "Hours") : "";
                    clientList.add(data);
                } catch (Exception inner) {
                    inner.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return clientList;
    }

    private static String safeGet(CSVRecord record, String header) {
        try {
            String v = record.isMapped(header) ? record.get(header) : "";
            return v == null ? "" : v.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
