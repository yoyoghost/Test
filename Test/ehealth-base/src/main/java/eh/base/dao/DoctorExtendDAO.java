package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.base.DoctorExtend;
import org.apache.log4j.Logger;

/**
 * Created by zhongzx on 2017/3/24 0024.
 */
@RpcSupportDAO
public abstract class DoctorExtendDAO extends HibernateSupportDelegateDAO<DoctorExtend>{
    public static final Logger log = Logger.getLogger(DoctorExtendDAO.class);

    public DoctorExtendDAO(){
        super();
        this.setEntityName(DoctorExtend.class.getName());
        this.setKeyField("doctorId");
    }

    @DAOMethod
    public abstract DoctorExtend getByDoctorId(int doctorId);

    @DAOMethod(sql = "update DoctorExtend set sealData=:sealData where doctorId=:doctorId")
    public abstract void updateSealDataById(@DAOParam("sealData") String sealData, @DAOParam("doctorId") int doctorId);

    /**
     * 保存或者更新 个性签名数据
     * @param sealData
     * @param doctorId
     * @return
     */
    public Boolean saveOrUpdateSealData(String sealData, int doctorId){
        DoctorExtend doctorExtend = getByDoctorId(doctorId);
        if(null == doctorExtend){
            doctorExtend = new DoctorExtend();
            doctorExtend.setDoctorId(doctorId);
            doctorExtend.setSealData(sealData);
            this.save(doctorExtend);
        }else{
            this.updateSealDataById(sealData, doctorId);
        }
        return true;
    }
}
