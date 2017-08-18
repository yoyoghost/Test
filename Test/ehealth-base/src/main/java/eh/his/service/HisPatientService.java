package eh.his.service;

import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.UserRolesDAO;
import eh.entity.base.UserRoles;
import eh.entity.his.hisCommonModule.Patient_HIS;
import eh.entity.mpi.FamilyMember;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.FamilyMemberDAO;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.task.executor.HisHealthCardSynExecutor;
import eh.task.executor.HisPatientSynExecutor;
import eh.utils.DateConversion;
import eh.wxpay.dao.WeixinMPDAO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 六院患者数据同步
 * Created by Zhangxq on 2017-7-27.
 */
public class HisPatientService {

    //六院appId
    private static final String LY_APPID = "wxa4432faba935a4b8";

    /**
     * 获取该机构新增的患者
     * 当天23:59
     * */
    @RpcService
    public void getNewPatientByOrgan(Integer organId){
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        List<FamilyMember> list = familyMemberDAO.findNewPatient(organId);
        if(CollectionUtils.isEmpty(list)){
            return ;
        }
        sendPatientToHis(organId,list,"I");
    }

    /**
     * 获取该机构修改信息的患者
     * 当天23:59
     * */
    @RpcService
    public void getModifyPatientByOrgan(Integer organId){
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        List<FamilyMember> list = familyMemberDAO.findModifyFamilyMember(organId);
        if(CollectionUtils.isEmpty(list)){
            return ;
        }
        sendPatientToHis(organId,list,"U");
    }

    private void sendPatientToHis(Integer organId,List<FamilyMember> list,String type){
        for(FamilyMember familyMember : list){
            Patient_HIS patient_his = getHisPatient(familyMember);
            patient_his.setType(type);
            try {
                new HisPatientSynExecutor(patient_his,organId).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 获取该机构新增的卡证
     * 当天23:59
     * */
    @RpcService
    public void getHealthCardByOrgan(Integer organId){
        Date now = new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String date = df.format(now);
        final Date startTime = DateConversion.getCurrentDate(date,"yyyy-MM-dd");
        List<HealthCard> cards = DAOFactory.getDAO(HealthCardDAO.class).findByOrganAndDate(organId, startTime);
        HisHealthCardSynExecutor executor = new HisHealthCardSynExecutor(cards,organId);
        try {
            executor.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private Patient_HIS getHisPatient(FamilyMember familyMember){
        Patient_HIS patient_his = new Patient_HIS();
        String mpiID = familyMember.getMemberMpi();
        String ownerMpiID = familyMember.getMpiid();
        Integer organId = familyMember.getOrganId();
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiID);
        patient_his.setMpiID(mpiID);
        patient_his.setOrganID(organId);
        patient_his.setPatientName(patient.getPatientName());
        patient_his.setPatientSex(patient.getPatientSex());
        patient_his.setBirthday(patient.getBirthday());
        patient_his.setPatientType(patient.getPatientType());
        patient_his.setIdCard(patient.getIdcard());
        patient_his.setIdCard2(patient.getIdcard2());
        patient_his.setMobile(patient.getMobile());
        patient_his.setOpenId(getOpenIdByPatient(patient));
        patient_his.setPatientStatus(patient.getStatus());
        patient_his.setOwnerMPI(ownerMpiID);
        patient_his.setFamilyMemberID(familyMember.getMemberId());
        patient_his.setMemberStatus(familyMember.getMemeberStatus());
        patient_his.setCreateDate(familyMember.getCreateDt());
        patient_his.setLastModify(familyMember.getLastModify());
        return patient_his;

    }
    /**
     * 根据patient获取openID
     * */
    private String getOpenIdByPatient(Patient patient){
        String loginId = patient.getLoginId();
        if(StringUtils.isEmpty(loginId)){
            return null;
        }
        UserRolesDAO userRolesDAO = DAOFactory.getDAO(UserRolesDAO.class);
        UserRoles userRole = userRolesDAO.getByUserIdAndRoleId(loginId, "patient");
        Integer userRoleId = userRole.getId();
        WeixinMPDAO weixinMPDAO = DAOFactory.getDAO(WeixinMPDAO.class);
        List<OAuthWeixinMP> res = weixinMPDAO.findByUrtAndAppId(userRoleId,LY_APPID);
        if(CollectionUtils.isEmpty(res)){
            return null;
        }
        return res.get(0).getOpenId();
    }
}
