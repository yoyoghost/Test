package test.opdao;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.base.dao.DoctorAccountDAO;
import eh.base.dao.DoctorAccountDetailDAO;
import eh.entity.base.DoctorAccountDetail;
import eh.op.service.DoctorWithdrawService;
import eh.op.tonglihr.TongliPaymentDetail;
import eh.op.tonglihr.TonglihrUtil;
import eh.op.tonglihr.UserInfo;
import eh.op.tonglihr.WithdrawInfo;
import eh.utils.MapValueUtil;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by houxr on 2016/9/8.
 */
public class TonglihrUtilTest extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    public void testTonglihr() {
        TonglihrUtil.apiTongliAddEmployees(getData());
    }

    public void testTonglihrForPayments() {
        System.out.println("====数据对接德科支付 start========\n");
        String result = TonglihrUtil.apiTongliForPayments(getWithdrawData());
        if (StringUtils.isNotEmpty(result)) {
            Map resultMap = JSONUtils.parse(result, Map.class);
            // code 200 成功 202请求已受理
            if (200 == MapValueUtil.getInteger(resultMap, "code")) {
                System.out.println("===msg===:" + MapValueUtil.getString(resultMap, "msg"));
                System.out.println("===data===:" + MapValueUtil.getString(resultMap, "data"));
            } else if (202 == MapValueUtil.getInteger(resultMap, "code")) {
                System.out.println("===msg===:" + MapValueUtil.getString(resultMap, "msg"));
                System.out.println("===data===:" + MapValueUtil.getString(resultMap, "data"));
            } else {
                System.out.println(result);
            }
        }
        System.out.println("====数据对接德科支付 end========\n");
    }


    public void testTongliGetBillIdResult() {
        try {
            String url = "https://api.tonglihr.com/test/open/1.0/payments/20161027175158?id=8b7e48cc65df11e6a60202eca976a4e9";
            System.out.println("\n根据批次号查询德科打款结果url:" + url);
            HttpClient httpClient = HttpClients.createDefault();
            HttpGet get = new HttpGet(url);
            HttpResponse response = httpClient.execute(get);
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            System.out.println("====查询德科支付结果========:" + content);
        } catch (Exception e) {
            System.out.println("====查询德科支付结果========\n");
        }

    }


    public void testTongliApiForPaymentEnd() {
        DoctorWithdrawService doctorWithdrawService = new DoctorWithdrawService();
        doctorWithdrawService.paymentInfoData2TongliApiPayments("20161028125713");
        //doctorWithdrawService.updateCashBillsPayEndStatusFromTongliApi();
        //doctorWithdrawService.findBillId("123123");
        //Map<String, Object> resultMap = TonglihrUtil.tongliApiForEndPayment("20161019133115");//20161019133115  20160510103534
        /*if (ObjectUtils.nullSafeEquals(200, MapValueUtil.getInteger(resultMap, "code"))) {
            String jsonStr = JSONUtils.toString(MapValueUtil.getObject(resultMap, "data"));
            WithdrawInfo withdrawInfo = MapValueUtil.fromJson(jsonStr, WithdrawInfo.class);
            System.out.println("toString:" + jsonStr);
            System.out.println("MapEntity:" + JSONUtils.toString(withdrawInfo));
        }*/
        /*System.out.println("MapEntity:" + JSONUtils.toString(resultMap));

        DoctorWithdrawService doctorWithdrawService=new DoctorWithdrawService();
        List<String> billIds=doctorWithdrawService.listBillIdByPayStatus(1);
        System.out.println("billIds:" + JSONUtils.toString(billIds));
        for(String billId:billIds){
            Map<String, Object> result = TonglihrUtil.tongliApiForEndPayment(billId);
            System.out.println(billId+"-billIdEnd:" + JSONUtils.toString(result));
        }*/
        /*String resultString=doctorWithdrawService.paymentInfoData2TongliApiPayments("20161019133115");
        System.out.println("==resultString==="+resultString);*/
    }

    public void testTolidata() {
        DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        DoctorAccountDAO deDao = DAOFactory.getDAO(DoctorAccountDAO.class);
        List<DoctorAccountDetail> detailList = detailDao.findNotTestDoctorByPayStatusAndInout(4, 2);
        System.out.println("提现申请医生信息:"+detailList);
        DoctorWithdrawService doctorWithdrawService = new DoctorWithdrawService();
        System.out.println("德科返回结果:"+doctorWithdrawService.doctorDataTotonglihr(detailList));
    }

    /**
     * 测试用数据
     *
     * @return
     */
    public static List<UserInfo> getData() {
        List<UserInfo> data = new ArrayList<UserInfo>();

        UserInfo userInfo = new UserInfo();
        userInfo.setName("杨腾蛟");
        userInfo.setId_number("41282219881227483X");
        userInfo.setMobile_number("18668436182");
        userInfo.setProvince("西藏自治区");
        userInfo.setCity("日喀则市");
        userInfo.setAddress("西藏自治区日喀则市");
        userInfo.setAlipay_id("18668436182");
        userInfo.setCard_no("585464664");
        userInfo.setBank_code("中国交通银行");
        userInfo.setCard_name("杨腾蛟");
        userInfo.setBank_branch("西藏自治区日喀则市支行");
        userInfo.setPay_mobile("18668436182");
        userInfo.setEmployee_number(9572);

        UserInfo userInfo2 = new UserInfo();
        userInfo2.setName("侯秀玩4");
        userInfo2.setId_number("330483197404174260");
        userInfo2.setMobile_number("13656662421");
        userInfo2.setProvince("浙江省");
        userInfo2.setCity("浙江省桐乡市");
        userInfo2.setAddress("浙江省桐乡市三区二号");
        userInfo2.setAlipay_id("13656662421@126.com");
        userInfo2.setCard_no("6227001540020358511");
        userInfo2.setBank_code("中国建设银行");
        userInfo2.setCard_name("侯秀玩4");
        userInfo2.setBank_branch("浙江省武义县支行");
        userInfo2.setPay_mobile("13656662421");
        userInfo.setEmployee_number(9811);

        data.add(userInfo);
        data.add(userInfo2);
        return data;
    }

    /**
     * 测试用数据
     *
     * @return
     */
    public static WithdrawInfo getWithdrawData() {
        WithdrawInfo data = new WithdrawInfo();
        List<TongliPaymentDetail> payment_detail = new ArrayList<TongliPaymentDetail>();

        data.setPayment_batch_id("20160510163534");
        data.setFunding_bank_code("中信银行");
        data.setFunding_amount(new BigDecimal(100.22));
        data.setFunding_date("2016-10-15");
        data.setPayment_count(2);
        data.setPayment_total(new BigDecimal(100.22));
        data.setReply_url("http://www.nagrihealth.com/payment/result");

        TongliPaymentDetail payDetail1 = new TongliPaymentDetail();
        payDetail1.setAccount_bank_code("中国建设银行");
        payDetail1.setAccount_bank_no("6227001540020358500");
        payDetail1.setAccount_bank_name("侯秀玩3");
        payDetail1.setMobile_number("13656662422");
        payDetail1.setPayment_amount(new BigDecimal(80.22));
        payDetail1.setWithdraw_order_id("20160510163534");
        payment_detail.add(payDetail1);

        TongliPaymentDetail payDetail2 = new TongliPaymentDetail();
        payDetail2.setAccount_bank_code("中国建设银行");
        payDetail2.setAccount_bank_no("6227001540020358511");
        payDetail2.setAccount_bank_name("侯秀玩4");
        payDetail2.setMobile_number("13656662423");
        payDetail2.setPayment_amount(new BigDecimal(20.00));
        payDetail2.setWithdraw_order_id("20160510163534");
        payment_detail.add(payDetail2);
        data.setPayment_detail(payment_detail);
        return data;
    }

    public static void main(String[] args) {
        /*try {
            String content = JSONUtils.toString(getData());
            String sign = getSign(content);
            System.out.println("加密前content:" + content);
            System.out.println("sign值MD5加密前:" + (content + KEY));
            String body = getBody(content);
            System.out.println("body加密后:" + body);
            String decrypted = AESUtils.aesDecrypt(body, KEY);
            System.out.println("解密后:" + decrypted);
            String result = tongliApiAddEmployee(ID, sign, body);
            System.out.println("请求结果返回值:" + JSONUtils.toString(result));
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        List<UserInfo> data = new ArrayList<>();
        TonglihrUtil.apiTongliAddEmployees(getData());
    }


}
