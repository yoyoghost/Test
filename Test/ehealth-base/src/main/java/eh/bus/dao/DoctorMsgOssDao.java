package eh.bus.dao;
/********************************************
 * 文件名称: DoctorMsgOssDao <br/>
 * 系统名称: feature4
 * 功能说明: TODO ADD FUNCTION. <br/>
 * 开发人员:  Chenq
 * 开发时间: 2017/3/30 15:43 
 *********************************************/

import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.bus.DoctorMsgOss;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RpcSupportDAO
public abstract class DoctorMsgOssDao extends HibernateSupportDelegateDAO<DoctorMsgOss> {
    private Logger logger = LoggerFactory.getLogger(DoctorMsgOssDao.class);

    public DoctorMsgOssDao() {
        super();
        this.setEntityName(DoctorMsgOss.class.getName());
        this.setKeyField("id");
    }
}
