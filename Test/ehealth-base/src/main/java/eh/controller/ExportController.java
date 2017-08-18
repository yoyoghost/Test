package eh.controller;

import ctd.mvc.weixin.support.HttpClientUtils;
import ctd.mvc.weixin.support.HttpStreamResponse;
import eh.base.service.BusActionLogService;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@Controller("exportController")
public class ExportController {

    public static final Logger log = Logger.getLogger(ExportController.class);

    private static String fr_url = "http://121.43.169.166/WebReport/ReportServer";

    @RequestMapping(value = "api/export")
    public void exportReport(HttpServletRequest request,
                             HttpServletResponse response) {
        response.setContentType("application/vnd.ms-excel");
        String fileName = "export";
        Map<String, String[]> map = request.getParameterMap();
        if (map == null) {
            return;
        }
        StringBuffer url = new StringBuffer(fr_url).append("?");
        int i = 0;
        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue()[0];
            if ("reportlet".equals(key)) {
                i += 1;
            }
            if ("format".equals(key)) {
                i += 1;
            }
            if ("filename".equals(key)) {
                fileName = value;
            }
            url.append(key).append("=").append(value).append("&");
        }
        if (i != 2) {
            log.error("Excel导出条件不足，url："+url);
            return;
        }
        response.setHeader("Content-Disposition", "attachment; filename="
                + fileName+".xls");
        HttpStreamResponse res = null;
        try {
            res = HttpClientUtils.doGetAsInputStream(url.substring(0, url.length() - 1));
        } catch (IOException e) {
            e.printStackTrace();
        }
        InputStream is = null;
        OutputStream os = null;
        if (res == null) {
            return;
        }
        try {
            is = res.getInputStream();
            os = response.getOutputStream();
            byte[] buf = new byte[1024];
            int len = 0;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
                os.flush();
            }
            is.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
