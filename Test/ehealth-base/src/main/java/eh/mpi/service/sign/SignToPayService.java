package eh.mpi.service.sign;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.ScratchableConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganConfigDAO;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.OrganConfig;
import eh.entity.bus.ConsultSet;
import eh.entity.mpi.Patient;
import eh.entity.mpi.SignRecord;
import eh.mpi.constant.SignRecordConstant;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.push.SmsPushService;
import eh.task.executor.WxRefundExecutor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author renzh
 * @date 2016/10/12 0012 下午 17:44
 */
public class SignToPayService {

    private SignRecordDAO signRecordDAO;
    private OrganConfigDAO organConfigDAO;
    private ConsultSetDAO consultSetDAO;
    private PatientDAO patientDAO;
    private DoctorDAO doctorDAO;
    private EmploymentDAO employmentDAO;

    public SignToPayService() {
        this.signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        this.organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        this.consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        this.patientDAO = DAOFactory.getDAO(PatientDAO.class);
        this.doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        this.employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
    }

    /**
     * 健康端点申请签约按钮（查询所选医生签约信息）
     *
     * @param doctorId
     * @param organId
     * @return
     */
    @RpcService
    public Map getConsultForPay(String mpiId,Integer doctorId, Integer organId) {
        Map map = new HashMap();
        ConsultSet consultSet = consultSetDAO.getDefaultConsultSet(doctorId);
        Doctor doctor = doctorDAO.get(doctorId);
        Patient patient = patientDAO.getByMpiId(mpiId);
        List<Employment> employmentList = employmentDAO.findByDoctorIdOrderBy(doctorId);
        if(employmentList.size()>0){
            doctor.setDepartment(employmentList.get(0).getDepartment());
        }
        OrganConfig organConfig = organConfigDAO.get(organId);
        Double oSignCost = (organConfig.getSignPrice() == null ? 0 : organConfig.getSignPrice()).doubleValue();
        Double oSignSubsidyPrice = (organConfig.getSignSubsidyPrice() == null ? 0 : organConfig.getSignSubsidyPrice()).doubleValue();
        BigDecimal signCost;
        Double signSubsidyPrice;
        if (consultSet.getCanSign()) {
            if (oSignCost > consultSet.getSignPrice()) {
                consultSet.setSignPrice(oSignCost);
            }
        } else {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该医生已关闭签约功能，请提醒他开启");
        }
        signSubsidyPrice = oSignSubsidyPrice;
        if (consultSet.getSignPrice() - signSubsidyPrice < 0) {
            signSubsidyPrice = consultSet.getSignPrice();
            signCost = new BigDecimal("0");
        } else {
            BigDecimal b1 = new BigDecimal(consultSet.getSignPrice().toString());
            BigDecimal b2 = new BigDecimal(signSubsidyPrice.toString());
            signCost = b1.subtract(b2);
        }
        map.put("consultSet", consultSet);
        map.put("signSubsidyPrice", signSubsidyPrice);
        map.put("signCost", signCost);
        map.put("patient", patient);
        map.put("doctor", doctor);
        map.put("preContract",organConfig.getPreContract());
        return map;
    }

    /**
     * 患者取消签约 申请
     *
     * @param mpiId
     * @param signRecordId
     */
    @RpcService
    public Boolean cancelRequestSign(String mpiId, Integer signRecordId) {
        Boolean flag = true;
        SignRecord signRecord = signRecordDAO.get(signRecordId);
        Integer oldStatus = signRecord.getRecordStatus();
        if (mpiId.equals(signRecord.getRequestMpiId()) && (signRecord.getRecordStatus() == 0 || signRecord.getRecordStatus() == 3)) {
            signRecord.setRecordStatus(SignRecordConstant.RECORD_STATUS_CANCEL);
            try {
                signRecordDAO.update(signRecord);
            } catch (DAOException e) {
                flag = false;
            }
        } else {
            if(signRecord.getRecordStatus() == 1){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该医生已同意您的签约申请，无法取消");
            }
            if(signRecord.getRecordStatus() == 2){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该医生已拒绝您的签约申请");
            }
        }

        if(signRecord.getSignCost()>0&&signRecord.getPayFlag()==1) {
            WxRefundExecutor executor = new WxRefundExecutor(signRecordId, ScratchableConstant.SCRATCHABLE_MODULE_SIGN);
            executor.execute();
        }

        if(oldStatus == 0) {
            AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class).fireEvent(new BussCancelEvent(signRecordId, BussTypeConstant.SIGN, signRecord.getDoctor()));
            AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(signRecordId, signRecord.getOrgan(), "SignCancel", "", 0);
        }

        return flag;
    }

}
