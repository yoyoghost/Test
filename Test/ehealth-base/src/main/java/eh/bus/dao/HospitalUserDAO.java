package eh.bus.dao;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.BeanUtils;
import eh.entity.bus.HospitalData;
import eh.entity.bus.HospitalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/7/1 0001.
 */
public abstract class HospitalUserDAO extends HibernateSupportDelegateDAO<HospitalUser> {
    private static final Logger log = LoggerFactory.getLogger(HospitalUserDAO.class);

    public HospitalUserDAO() {
        super();
        setEntityName(HospitalUser.class.getName());
        setKeyField("id");
    }

    @DAOMethod(sql = "FROM HospitalUser WHERE processed=0")
    public abstract List<HospitalUser> findUnProcessHospitalUser();

    public void batchSaveHospitalData(final List<HospitalData> hospitalDataList) {
        for (HospitalData hd : hospitalDataList) {
            try {
                HospitalUser hu = BeanUtils.map(hd, HospitalUser.class);
                hu.setCreateTime(new Date());
                hu.setProcessed(0);
                save(hu);
            } catch (Exception e) {
                log.error("save hd[{}] error, errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(hd), e.getStackTrace(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }
    }

    @DAOMethod(sql = "UPDATE HospitalUser SET processed=:processed WHERE id=:id")
    public abstract void updateHospitalUserProcessed(@DAOParam("id") Integer id,
                                                     @DAOParam("processed") int processed);


}
