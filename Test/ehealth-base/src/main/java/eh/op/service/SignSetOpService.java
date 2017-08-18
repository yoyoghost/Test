package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.base.OrganConfig;
import eh.entity.bus.ConsultSet;
import org.apache.log4j.Logger;

/**
 * Created by houxr on 2016/9/21.
 */
public class SignSetOpService {

    public static final Logger logger = Logger.getLogger(SignSetOpService.class);

    /**
     * [运营平台]医生签约设置服务
     *
     * @param consultSet
     */
    @RpcService
    public void updateDoctorConsultSetForOp(ConsultSet consultSet) {
        if (consultSet == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "consultSet is required");
        }
        if (consultSet.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(consultSet.getDoctorId());
        Organ organ = organDAO.getByOrganId(doctor.getOrgan());
        OrganConfig organConfig = organConfigDAO.getByOrganId(doctor.getOrgan());
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);

        // 如果 canSign 有值，将 signStatus 设置和 canSign 相同
        if (consultSet.getCanSign() != null) {
            consultSet.setSignStatus(consultSet.getCanSign());
        }
        ConsultSet target = consultSetDAO.getDefaultConsultSet(consultSet.getDoctorId());
        if (target == null) {
            target = consultSet;
        } else {
            if (!Boolean.TRUE.equals(organConfig.getCanSign())&&consultSet.getCanSign()!=null&& consultSet.getCanSign()) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "医生所在机构未打开签约功能");
            }
            BeanUtils.map(consultSet, target);
            /* 由于设置了 signStatus 值和 canSign 相同所以无需 updateCanSignOP 用 addOrupdateConsultSetAdmin 方法会修改 signStatus
            target.setCanSign(consultSet.getCanSign());
	        target.setSignStatus(consultSet.getCanSign());
	        consultSetDAO.updateCanSignOP(consultSet.getCanSign(), consultSet.getDoctorId());
            */
        }
        consultSetDAO.addOrupdateConsultSetAdmin(target);
        BusActionLogService.recordBusinessLog("医生信息修改", doctor.getDoctorId().toString(), "ConsultSet",
                "[" + organ.getShortName() + "]的[" + doctor.getName() + "](" + doctor.getDoctorId() + ")医生的ConsultSet相关设置属性值被修改");
    }

}
