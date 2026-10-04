package com.gak.travelrecord.service;

import com.gak.travelrecord.domain.TravelRecord;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import org.springframework.stereotype.Component;

/** CSV 只导出当前用户的结构化数据，公式前缀转义防止电子表格执行。 */
@Component
public class TravelCsvWriter {
    public void write(Writer writer, List<TravelRecord> records) throws IOException {
        writer.write('\ufeff');
        writer.write("记录ID,分组ID,原改签记录ID,交通类型,状态,出发地,到达地,出发日期,出发时间,出发时区,到达日期,到达时间,到达时区,车次航班,承运公司,席别舱位,车厢,座位,用途,购票平台,订单号,支付金额,退款金额,币种,支付日期,退款日期,出发日期代记,同步账单,备注\r\n");
        for (TravelRecord record : records) {
            Object[] cells = {record.getId(), record.getGroupId(), record.getPreviousRecordId(), record.getTransportType(),
                record.getStatus(), record.getDeparture(), record.getArrival(), record.getDepartureDate(), record.getDepartureTime(),
                record.getDepartureZone(), record.getArrivalDate(), record.getArrivalTime(), record.getArrivalZone(),
                record.getServiceNumber(), record.getCarrier(), record.getSeatClass(), record.getCarriage(), record.getSeat(),
                record.getPurpose(), record.getPlatform(), record.getOrderNumber(), record.getPaidAmount(), record.getRefundAmount(),
                record.getCurrency(), record.getPaymentDate(), record.getRefundDate(), record.getUseDepartureDate(),
                record.getSyncBill(), record.getNote()};
            for (int index = 0; index < cells.length; index++) {
                if (index > 0) { writer.write(','); }
                writer.write(escape(cells[index]));
            }
            writer.write("\r\n");
        }
    }
    String escape(Object value) {
        String text = value == null ? "" : value.toString();
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) { text = "'" + text; }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
