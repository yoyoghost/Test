package test.dao;

import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据手机号 生成 or 获取医生二维码图片
 * Created by houxr on 2016/5/16.
 */
public class DownloadJPGFromUrlTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    private static DoctorDAO dao = appContext.getBean("doctorDAO", DoctorDAO.class);

    /**
     * 根据手机号码获取 医生二维码图片
     */
    public void testGetTicketAndUrlByDoctorId() {
        String s="13957120890,13858060709,13958166572," +
                "13758143571,13805752614,13867469261," +
                "13957178822,13868101010,18758205727";
        List<String> list = new ArrayList<String>();
        Doctor doctor=null;
        String[] newstr = s.split(",");
        for(int i =0;i<newstr.length;i++){
            list.add(newstr[i]);
        }
        List<Doctor> doctorList=new ArrayList<Doctor>();
        for(int j=0;j<list.size();j++){
            doctor=dao.getByMobile(list.get(j));
            dao.getTicketAndUrlByDoctorId(doctor.getDoctorId());
            //doctorList.add(doctor);
            System.out.println(doctor.getName());
        }
        System.out.println("====二维码生成end===="+doctorList.size());
    }

    /**
     * 下载文件到本地
     * @param urlString 被下载的文件地址
     * @param filename 本地文件名
     * @throws Exception 各种异常
     */
    public static void download(String urlString, String filename) throws Exception {
        // 构造URL
        URL url = new URL(urlString);
        // 打开连接
        URLConnection con = url.openConnection();
        // 输入流
        InputStream is = con.getInputStream();
        // 1K的数据缓冲
        byte[] bs = new byte[1024];
        // 读取到的数据长度
        int len;
        // 输出的文件流
        OutputStream os = new FileOutputStream(filename);
        // 开始读取
        while ((len = is.read(bs)) != -1) {
            os.write(bs, 0, len);
        }
        // 完毕，关闭所有链接
        os.close();
        is.close();
    }

    public void getWxDoctorPhoto(String mobile) {
        try{
            if(!StringUtils.isEmpty(mobile)) {
                Doctor doctor = dao.getByMobile(mobile);
                //从阿里云图片服务器上下载图片
                // 开发库 http://ngaribata.ngarihealth.com:8380/ehealth-base-dev/upload/10000797
                // 正式库 http://ehealth.easygroup.net.cn/ehealth-base/upload/14090
                download("http://ngaribata.ngarihealth.com:8380/ehealth-base-dev/upload/" + doctor.getQrCode(),
                        "E:/wxphoto/" + doctor.getName() + "_" + doctor.getMobile() + ".jpg");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void testGetPhoto() {
        getWxDoctorPhoto("15268293359");
    }

}
