package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.base.constant.DoctorTabConstant;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.Doc;
import java.util.List;

/**
 * Created by zhangsl on 2017/5/25.
 */
public abstract class DoctorTabDAO extends HibernateSupportDelegateDAO<DoctorTab> {
    public static final Logger logger = LoggerFactory.getLogger(DoctorTabDAO.class);

    public DoctorTabDAO(){
        super();
        this.setEntityName(DoctorTab.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据医生内码和扩展类型查询扩展内容
     * @param doctorId
     * @param paramType
     * @return
     */
    @DAOMethod
    public abstract DoctorTab getDoctorTabByDoctorIdAndParamType(int doctorId,int paramType);

    /**
     * 根据医生内码查询会诊类型（true:会诊中心）
     * @param doctorId
     * @return
     */
    public Boolean getMeetTypeByDoctorId(int doctorId) {
        DoctorTab doctorTab = getDoctorTabByDoctorIdAndParamType(doctorId, DoctorTabConstant.ParamType_MEETCENTER);
        return (doctorTab != null && doctorTab.getParamValue() != null && doctorTab.getParamValue().equals(DoctorTabConstant.ParamValue_TRUE));
    }

    @DAOMethod
    public abstract List<DoctorTab> findByDoctorId(Integer doctorId);




}
