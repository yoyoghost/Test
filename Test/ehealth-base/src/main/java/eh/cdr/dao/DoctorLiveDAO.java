package eh.cdr.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.cdr.DoctorLive;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.util.Date;

/**
 * Created by liuya on 2017-7-5.
 */
public abstract class DoctorLiveDAO extends HibernateSupportDelegateDAO<DoctorLive> {

    private static final Log logger = LogFactory.getLog(DoctorLiveDAO.class);

    public DoctorLiveDAO(){
        super();
        this.setEntityName(DoctorLive.class.getName());
        this.setKeyField("liveId");
    }

    //根据医生id获取直播间信息
    @RpcService
    @DAOMethod
    public abstract DoctorLive getByDoctorId(Integer doctorId);

    @DAOMethod(sql = "update DoctorLive set URL=:url where doctorId=:doctorId")
    public abstract void updateURLByDoctorId(@DAOParam("doctorId") Integer doctorId, @DAOParam("url") String url);

    //判断医生是否开启直播并获取直播间信息
    @DAOMethod(sql = "from DoctorLive where doctorId=:doctorId and endDate>:now and startDate<:now")
    public abstract  DoctorLive getLivingById(@DAOParam("doctorId") Integer doctorId, @DAOParam("now") Date now);

    /**
     * 保存或修改直播间
     * @param doctorLive
     * @param update
     * @return
     */
    public Integer saveOrUpdateDoctorLive(final DoctorLive doctorLive, final boolean update){
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorLive dl;
                if(update){
                    dl = update(doctorLive);
                } else {
                    dl = save(doctorLive);
                }
                setResult(dl.getDoctorId());
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

}
