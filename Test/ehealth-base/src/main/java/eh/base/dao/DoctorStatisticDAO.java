package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.converter.ConversionUtils;
import eh.entity.base.DoctorStatistic;
import eh.utils.LocalStringUtil;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/7/19.
 */

public abstract class DoctorStatisticDAO extends HibernateSupportDelegateDAO<DoctorStatistic> {

    public static final Logger logger = LoggerFactory.getLogger(DoctorStatisticDAO.class);

    public DoctorStatisticDAO() {
        super();
        this.setEntityName(DoctorStatistic.class.getName());
        this.setKeyField("doctorId");
    }


    /**
     * 计算患者和医生关注此医生的数量
     *
     * @return
     * @author cuill
     * @date 2017/7/19
     */
    public List<Object[]> autoCalculateAttentionCount() {
        AbstractHibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "SELECT a.DoctorId, IFNULL(b.num, 0) + IFNULL(c.num, 0) AS num FROM base_doctor a LEFT JOIN (SELECT COUNT(*) AS num, RelationDoctorID FROM base_relationdoctor GROUP BY RelationDoctorID ) b ON b.RelationDoctorID = a.DoctorId LEFT JOIN (SELECT COUNT(*) AS num, DoctorId FROM MPI_RelationDoctor WHERE relationType = 1 GROUP BY DoctorId ) c ON c.DoctorId = a.DoctorId";
                Query query = ss.createSQLQuery(sql);
                List<Object[]> objectList = query.list();
                setResult(objectList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 更新医生统计表的关注数
     *
     * @param attentionCount 关注数量统计
     * @param doctorId       医生的主键
     * @param modifyTime     修改时间
     * @date 2017/7/19
     * @author cuill
     */
    @DAOMethod(sql = "update Doctor set attentionCount=:attentionCount and modifyTime=:modifyTime where doctorId =:doctorId ")
    public abstract void updateFeedbackCountToInitByTypeAndIdForSchedule(@DAOParam("attentionCount") Long attentionCount,
                                                                         @DAOParam("doctorId") Integer doctorId,
                                                                         @DAOParam("modifyTime") Date modifyTime);


}
