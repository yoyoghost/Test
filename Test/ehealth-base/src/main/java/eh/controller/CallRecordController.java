package eh.controller;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.xml.XMLHelper;
import eh.bus.dao.CallRecordDAO;
import eh.entity.bus.CallRecord;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;

/**
 * Created by xuqh on 2016/4/20.
 */
@Controller("callRecordController")
public class CallRecordController {

    public static final Logger log = Logger.getLogger(CallRecordController.class);

    /**
     * 用于接收通话录音下载链接
     * <p>
     * eh.controller
     *
     * @author luf 2016-6-23
     */
    @RequestMapping(value = "call/log")
    public void callLog(HttpServletRequest httpServletRequest, HttpServletResponse res) {
        log.info("------------------------开始----------------------------");
        try {
            HashMap<String, Object> map = JSONUtils.parse(httpServletRequest.getInputStream(), HashMap.class);
            log.info("通话录音下载链接====>  " + JSONUtils.toString(map));
            if (null != map && !StringUtils.isEmpty(map.get("callId")) && null != map.get("callTime")) {
                String callSid = String.valueOf(map.get("callId"));
                CallRecord call = new CallRecord();
                CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
                call = dao.getByCallSid(callSid);
                if (null != call) {
                    Integer callTime = Integer.parseInt(map.get("callTime").toString());
                    if (callTime == null) {
                        callTime = 0;
                    }
                    call.setCallTime(callTime);
                    if (!StringUtils.isEmpty(map.get("rec"))) {
                        call.setUrl(String.valueOf(map.get("rec")));
                    }
                    dao.update(call);
                } else {
                    log.error("CallRecordController====>callLog:callSid=" + callSid);
                }
                PrintWriter writer = res.getWriter();
                HashMap<String, Object> result = new HashMap<String, Object>();
                result.put("code", 200);
                writer.print(result);
            }
        } catch (IOException e) {
           log.error(e);
        }
    }

    /**
     * 用于接收通话录音下载链接
     * <p>
     * eh.controller
     *
     * @author luf 2016-4-5
     * <p>
     * byetype 挂机类型 正常挂机1: 通话中取消回拨、直拨和外呼的正常结束通话2:
     * 账户欠费或者设置的通话时间到3:回拨通话中主叫挂断，正常结束通话4:回拨通话中被叫挂断，正常结束通话 通用类型-1:
     * 被叫没有振铃就收到了挂断消息-2: 呼叫超时没有接通被挂断-5: 被叫通道建立了被挂断-6: 系统鉴权失败-7:
     * 第三方鉴权失败-11: 账户余额不足 直拨类型-8: 直拨被叫振铃了挂断 回拨类型-3: 回拨主叫接通了主叫挂断-4:
     * 回拨主叫通道创建了被挂断-9: 回拨被叫振铃了挂断-10: 回拨主叫振铃了挂断-14:回拨取消呼叫(通过取消回拨接口)
     */
    @RequestMapping(value = "call/record")
    public void callRecord(HttpServletRequest httpServletRequest, HttpServletResponse res) {
        log.info("------------------------开始----------------------------");
        try {
            InputStream io = httpServletRequest.getInputStream();
            final String result = getStreamString(io);
            log.info("通话录音下载链接====>  " + result);
            if (StringUtils.isEmpty(result)) {
                log.error("result is null !");
            }

            Document docr = DocumentHelper.parseText(result);
            Element cdr = docr.getRootElement();
            // 应用ID
            String appId = cdr.elementText("appId");
            // 回拨接口请求后响应返回的callSid参数，一路呼叫的唯一标识。
            String callSid = cdr.elementText("callSid");
            // 主叫号码，对应回拨接口中from参数
            String caller = cdr.elementText("caller");
            // 被叫号码，对应回拨接口中to参数
            String called = cdr.elementText("called");
            // 开始时间，如果被叫接听则是被叫摘机时间，否则是主叫摘机时间，如果主叫未接听则为空。YYYYMMDDHH24MISS
            String starttime = cdr.elementText("starttime");
            // 结束时间，如果被叫接听则是被叫挂机时间，否则是主叫挂机时间，如果主叫未接听则为空。YYYYMMDDHH24MISS
            String endtime = cdr.elementText("endtime");
            // 通话时长，如果被叫接听则是被叫通话时长，否则是主叫通话时长，如果主叫未接听则为0.单位秒
            String duration = cdr.elementText("duration");
            // 开始呼叫主叫时间 YYYYMMDDHH24MISS
            String beginCallTime = cdr.elementText("beginCallTime");
            // 主叫开始振铃时间 YYYYMMDDHH24MISS
            String ringingBeginTime = cdr.elementText("ringingBeginTime");
            // 主叫结束振铃时间，也是主叫摘机时间，等于starttime YYYYMMDDHH24MISS
            String ringingEndTime = cdr.elementText("ringingEndTime");
            // 挂机类型-见文档注释
            String byetype = cdr.elementText("byetype");
            // 通话录音下载地址，当回拨接口中needRecord参数设置录音且主叫摘机才会有下载地址，否则没有此参数。
            // 注：因为录音文件需要时间同步到下载服务器，建议在获取到录音下载地址10秒后再进行下载。http方式
            String recordurl = cdr.elementText("recordurl");

            // 更新通话时长和录音下载地址
            CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
            CallRecord call = dao.getByCallSid(callSid);

            Integer callTime = 0;
            callTime = Integer.valueOf(duration);
            call.setCallTime(callTime);
            call.setUrl(recordurl);
            dao.update(call);

            PrintWriter writer = res.getWriter();
            Document docu = XMLHelper.createDocument();
            Element response = docu.addElement("Response");
            Element statuscode = response.addElement("statuscode");
            statuscode.setText("000000");
            String resp = docu.asXML();
            writer.print(resp);
        } catch (IOException e) {
            log.error(e);
        } catch (DocumentException e) {
            log.error(e);
        }
    }


    private static String getStreamString(InputStream tInputStream) {
        if (tInputStream != null) {
            try {
                BufferedReader tBufferedReader = new BufferedReader(
                        new InputStreamReader(tInputStream));
                StringBuffer tStringBuffer = new StringBuffer();
                String sTempOneLine;
                while ((sTempOneLine = tBufferedReader.readLine()) != null) {
                    tStringBuffer.append(sTempOneLine);
                }
                return tStringBuffer.toString();
            } catch (Exception ex) {
                log.error(ex);
            }finally {
                try {
                    tInputStream.close();
                } catch (IOException e) {
                   log.error(e);
                }
            }
        }
        return null;
    }

//    public static void main(String[] args) {
//        //{calledNum=15990092533, businessName=YLDC, calledShowNum=87237144, hangupTime=2016-06-27 10:32:29, callTime=0,
//        // calledBeginTime=2016-06-27 10:31:49, isRec=3000, callId=398ea975d4694c899c031a30c8e61cb8, callShowNum=87237144,
//        // rec=, callBeginTime=2016-06-27 10:31:39, callAnswerTime=2016-06-27 10:31:49, callShowMode=1302, callNum=13735891715}
//        HashMap<String, Object> map = new HashMap<String, Object>();
//        map.put("calledNum", "15990092533");
//        map.put("businessName", "YLDC");
//        map.put("calledShowNum", "87237144");
//        map.put("hangupTime", "2016-06-27 10:32:29");
//        map.put("callTime", "0");
//        map.put("calledBeginTime", "2016-06-27 10:31:49");
//        map.put("isRec", "3000");
//        map.put("callId", "398ea975d4694c899c031a30c8e61cb8");
//        map.put("callShowNum", "87237144");
//        map.put("rec", "");
//        map.put("callBeginTime", "2016-06-27 10:31:39");
//        map.put("callAnswerTime", "2016-06-27 10:31:49");
//        map.put("callShowMode", "1302");
//        map.put("callNum", "13735891715");
//        String a = null;
//        try {
////            a = HttpClientUtils.doPostJson("http://ngaribata.ngarihealth.com:8780/ehealth-base-feature2/call/log", map);
//            a = HttpClientUtils.doPostJson("http://localhost:8082/ehealth-base/call/log", map);//JSONUtils.toString(map, Map.class));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
