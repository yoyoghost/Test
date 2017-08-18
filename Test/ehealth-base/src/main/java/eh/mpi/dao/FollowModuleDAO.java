package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.annotation.RpcService;
import eh.entity.mpi.FollowModule;
import org.hibernate.Query;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * @author renzh
 * @date 2016/10/9  下午 15:00
 */
public abstract class FollowModuleDAO extends HibernateSupportDelegateDAO<FollowModule> {

    public FollowModuleDAO() {
        super();
        this.setEntityName(FollowModule.class.getName());
        this.setKeyField("mid");
    }

    @DAOMethod(sql = "from FollowModule where display = 1")
    public abstract List<FollowModule> findAll();

    @DAOMethod(sql = "from FollowModule")
    public abstract List<FollowModule> findByLimitAndStart(@DAOParam(pageStart = true) int start,
                                                           @DAOParam(pageLimit = true) int limit);

    @DAOMethod
    public abstract FollowModule getByMid(Integer mid);

    @RpcService
    @DAOMethod()
    public abstract void deleteByMid(Integer mid);

    @DAOMethod(sql=" select count(mid) from FollowModule")
    public abstract Long getCount();

    /**
     * 查询在前端显示的模板总数
     * @return
     */
    @DAOMethod(sql = "select count(mid) from FollowModule where display = 1 and status = 1")
    public abstract Long getDisplayCount();

    /**
     * 分页查询 医生--我的模板列表 使用过的模板按照使用次数排在前面
     * 增加隐私配置
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    public List<FollowModule> findModuleListByDoctorIdWithPage(final Integer doctorId,
    														   final Integer organId,
    														   final Integer deptId,
                                                               final Integer start,
                                                               final Integer limit){
        AbstractHibernateResultAction<List<FollowModule>> action = new AbstractHibernateResultAction<List<FollowModule>>() {
            @Override
            public void execute(Session ss) throws Exception {
                StringBuilder sql = new StringBuilder("select f.* from mpi_followmodule f LEFT JOIN " +
                        "(select moduleId, count(1) as num from " +
                        "(select moduleId, planId from mpi_followplan where planCreator=:doctorId " +
                        "and moduleId is not null group by planId) dd " +
                        "GROUP BY moduleId) d on f.mid = d.moduleId where f.display = 1 and f.status = 1 and (universally = 1 or (universally = 0 " +
                        "and f.MID in(select distinct MID from mpi_followmodullimit where mid=f.mid and " +
                        "((organId =:organId and deptId = :deptId) or (organId =:organId and deptId = 0)) " +
                        "and activeFlag = 1))) " +
                        "ORDER BY d.num desc");
                Query q = ss.createSQLQuery(sql.toString());
                q.setParameter("deptId", deptId);
                q.setParameter("organId", organId);
                q.setParameter("doctorId", doctorId);
                if(null != start) {
                    q.setFirstResult(start);
                }
                if(null != limit) {
                    q.setMaxResults(limit);
                }
                @SuppressWarnings("unchecked")
                List<Object[]> list = q.list();
                List<FollowModule> followModuleList = new ArrayList<>();
                for(Object[] obj:list){
                    FollowModule followModule = new FollowModule();
                    followModule.setMid(Integer.valueOf(obj[0].toString()));
                    followModule.setTitle(obj[1].toString());
                    followModule.setPlanNum(null==obj[2]?0:Integer.valueOf(obj[2].toString()));
                    followModule.setRemark(null==obj[3]?"":obj[3].toString());
                    followModuleList.add(followModule);
                }
                setResult(followModuleList);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

}
