package test.dao;

import ctd.util.JSONUtils;
import eh.bus.dao.InHospitalPrepaidDAO;
import eh.entity.his.fee.InHospitalPrepaidResponse;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by hwg on 2016/11/2.
 */
public class InHospitalPrepaidDAOTester extends TestCase{
    private static ClassPathXmlApplicationContext appContext;
    private static InHospitalPrepaidDAO dao;
    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao=appContext.getBean("inHospitalDAO", InHospitalPrepaidDAO.class);
    }

    public void testGetByMrnAndSeriesAndInterId(){
        String mrn = "201111";
        String series = "111101";
        String interid ="32421";
        //InHospitalPrepaidResponse inHos = dao.getByMrnAndSeriesAndInterId(mrn,series,interid);
        //System.out.println(JSONUtils.toString(inHos));
    }

    public void testUpdateInHospital(){
        String mrn = "201111"; //病历号
        String series ="111101"; //序列号
        String interid ="32421"; //费用单号
        String pname ="maybe"; //患者名字
        String deptname ="外科"; //科室名称
        String bedid ="4"; //床位号
        String hospital ="温附"; //住院名称
        String floor ="10"; //楼层
        String position ="大江东"; //地点
        String indate ="2016-11-02"; //住院日期
        String service ="自费"; //医保类型
        String prepayment="8000"; //预交款总额
        String totalFee="4000"; //已产生总费用
        String balance="4000"; //预交款余额
        int organId =1000358;
        dao.updateInHospital(mrn,series,interid,pname,deptname,bedid,hospital,floor,position,indate,service,prepayment,totalFee,balance,organId);
    }

    public void testUpdatePerPayAndBalance(){
        String interid = "32421";
        int organId = 1;
        double prepayment = 6000;
        double balance =3000;
        double total = prepayment - balance;
        dao.updatePerPayAndBalance(String.valueOf(prepayment),String.valueOf(balance),String.valueOf(total),interid,organId);
    }

    public void testGetByInterId(){
        String balance = "161122";
        //InHospitalPrepaidResponse in = dao.getByInterId(balance);
        //String balance2 = in.getBalance();
        //System.out.println(balance2);
    }

    public void testSaveInHospitalPrepaid(){

        InHospitalPrepaidResponse in = new InHospitalPrepaidResponse();
        in.setMrn("8013441");
        in.setSeries("49");
        in.setInterid("161122");
        in.setPname("郑芬");
        in.setDeptname("头颈外科");
        in.setBedid("318111");
        in.setHospital("庆春院区");
        in.setFloor("3");
        in.setPosition("三_十八护士站");
        in.setIndate("2016/8/29 8:32:01");
        in.setPrepayment("18000.00");
        in.setTotalFee("1030.50");
        in.setBalance("16969.50");
        in.setService("市级医保");
        in.setOrganId(1000422);
        dao.saveInHospitalPrepaid(in);
    }
}
