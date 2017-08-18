package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import eh.entity.his.fee.InHospitalPrepaidResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by hwg on 2016/11/1.
 */
public abstract class InHospitalPrepaidDAO extends HibernateSupportDelegateDAO<InHospitalPrepaidResponse> {

    private static final Logger logger = LoggerFactory.getLogger(InHospitalPrepaidDAO.class);
    public InHospitalPrepaidDAO(){
        super();
        this.setEntityName(InHospitalPrepaidResponse.class.getName());
        this.setKeyField("id");
    }

    public void saveInHospitalPrepaid(InHospitalPrepaidResponse ipr){
        logger.info("InHospitalPrepaidResponse:{}", JSONUtils.toString(ipr));
        if (ipr == null){
            logger.info("返回数据为空,不做任何操作");
            return;
        }
        String mrn = ipr.getMrn();
        String series = ipr.getSeries();
        String interid = ipr.getInterid();
        InHospitalPrepaidResponse res = getByMrnAndSeriesAndInterIdAndOrganId(mrn,series,interid,ipr.getOrganId());
        if (res == null){
            save(ipr);
            logger.info("查询没有相同数据,保存成功");

        }else{
            String SMrn = res.getMrn();
            String SSeries = res.getSeries();
            String SInterid = res.getInterid();
            if (mrn.equals(SMrn) && series.equals(SSeries) && interid.equals(SInterid)){
                String pname = ipr.getPname();
                String deptname = ipr.getDeptname();
                String bedid = ipr.getBedid();
                String hospital = ipr.getHospital();
                String floor = ipr.getFloor();
                String position = ipr.getPosition();
                String indate = ipr.getIndate();
                String service = ipr.getService();
                String prepayment = ipr.getPrepayment();
                String totalFee = ipr.getTotalFee();
                String balance = ipr.getBalance();
                int organId = ipr.getOrganId();
                this.updateInHospital(mrn,series,interid,pname,deptname,bedid,hospital,floor,position,indate,service,prepayment,totalFee,balance,organId);
                logger.info("查询有相同数据,更新成功");
            }
        }

    }
    @DAOMethod(sql = "from InHospitalPrepaidResponse where mrn =:mrn and series =:series and interid =:interid and organId=:organId")
    public abstract InHospitalPrepaidResponse getByMrnAndSeriesAndInterIdAndOrganId(@DAOParam("mrn") String mrn, @DAOParam("series") String series, @DAOParam("interid") String interid,@DAOParam("organId") int organId);

    @DAOMethod(sql = "update InHospitalPrepaidResponse set pname =:pname, deptname =:deptname, bedid =:bedid, hospital =:hospital, floor =:floor, position =:position, indate =:indate, service =:service, prepayment=:prepayment, totalFee =:totalFee, balance =:balance  where organId =:organId and mrn =:mrn and series =:series and interid =:interid")
    public abstract void updateInHospital(@DAOParam("mrn") String mrn, @DAOParam("series") String series, @DAOParam("interid") String interid, @DAOParam("pname") String pname, @DAOParam("deptname")String deptname, @DAOParam("bedid")String bedid,@DAOParam("hospital") String hospital,@DAOParam("floor") String floor, @DAOParam("position") String position, @DAOParam("indate") String indate, @DAOParam("service") String service, @DAOParam("prepayment") String prepayment, @DAOParam("totalFee") String totalFee, @DAOParam("balance") String balance ,@DAOParam("organId") int organId);

    @DAOMethod(sql = "update InHospitalPrepaidResponse set prepayment =:prepayment, balance =:balance, totalFee =:totalFee where interid =:interid and organId =:organId")
    public abstract void updatePerPayAndBalance(@DAOParam("prepayment") String prepayment, @DAOParam("balance") String balance, @DAOParam("totalFee") String totalFee, @DAOParam("interid") String interid, @DAOParam("organId") int organId);

    @DAOMethod(sql = "from InHospitalPrepaidResponse where interid =:interid and organId=:organId")
    public abstract InHospitalPrepaidResponse getByInterIdAndOrganId(@DAOParam("interid") String interid,@DAOParam("organId") int organId);




}
