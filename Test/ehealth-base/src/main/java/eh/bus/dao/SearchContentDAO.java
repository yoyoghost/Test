package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.entity.bus.SearchContent;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Created by luf on 2016/10/8.
 */

public abstract class SearchContentDAO extends HibernateSupportDelegateDAO<SearchContent>
        implements DBDictionaryItemLoader<SearchContent> {

    public SearchContentDAO() {
        super();
        this.setEntityName(SearchContent.class.getName());
        this.setKeyField("searchId");
    }

    /**
     * 增加搜索记录
     *
     * @param searchContent
     * @param flag          添加入口-0患者端1医生端
     */
    @RpcService
    public void addSearchContent(SearchContent searchContent, int flag) {
        if (searchContent == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "searchContent is required!");
        }
        if (flag == 0 && StringUtils.isEmpty(searchContent.getMpiId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        if (flag == 1) {
            if (searchContent.getDoctorId() == null || searchContent.getDoctorId() <= 0) {
                throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required!");
            }
            if (searchContent.getBussType() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "bussType is required!");
            }
        }
        if (StringUtils.isEmpty(searchContent.getContent())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "content is required!");
        }
        if (searchContent.getBussType() == null) {
            searchContent.setBussType(0);
        }
        searchContent.setStatus(1);
        this.save(searchContent);
    }

    /**
     * 查询最近10条搜索记录
     *
     * @param mpiId
     * @return
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select distinct content from SearchContent where mpiId=:mpiId and status = 1 order by searchId desc")
    public abstract List<String> findContentByMpiId(@DAOParam("mpiId") String mpiId);

    /**
     * 查询最近10条搜索记录-医生端
     *
     * @param doctorId
     * @param bussType
     * @return
     */
    @RpcService
    @DAOMethod(limit = 20, sql = "select distinct content from SearchContent where doctorId=:doctorId and bussType=:bussType and status=1 order by searchId desc")
    public abstract List<String> findContentByMpiIdAndBussType(@DAOParam("doctorId") int doctorId, @DAOParam("bussType") int bussType);

    /**
     * 区分业务查询最近10条搜索记录
     *
     * @param mpiId
     * @param bussType
     * @return
     */
    @RpcService
    public List<String> findContentsByMpiIdWithBuss(final String mpiId, final int bussType) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("select distinct content from SearchContent where mpiId=:mpiId ");
                if (bussType == 0) {
                    hql.append("and (bussType is null or bussType=:bussType) ");
                } else {
                    hql.append("and bussType=:bussType ");
                }
                hql.append(" and status=1 order by searchId desc");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("bussType", bussType);
                q.setParameter("mpiId", mpiId);
                q.setMaxResults(10);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select searchId from SearchContent where doctorId=:doctorId and content=:content and bussType=:bussType and status = 1")
    public abstract List<Integer> findSearchIdsByContentAndDoctorIdAndBussType(
            @DAOParam("doctorId") int doctorId, @DAOParam("content") String content,
            @DAOParam("bussType") int bussType);


    @DAOMethod(sql = "select searchId from SearchContent where mpiId=:mpiId and content=:content and bussType=:bussType and status = 1")
    public abstract List<Integer> findSearchIdsByContentAndMpiIddAndBussType(
            @DAOParam("mpiId") String mpiId, @DAOParam("content") String content,
            @DAOParam("bussType") int bussType);
    /**
     * 删除单条搜索记录-医生端
     *
     * @param doctorId 医生内码
     * @param content  搜索内容
     * @param bussType 搜索业务类型
     * @return Boolean
     */
    @RpcService
    public Boolean deleteOneSearchContent(int doctorId, String content, int bussType) {
        if (StringUtils.isEmpty(content)) {
            return false;
        }
        List<Integer> searchIds = this.findSearchIdsByContentAndDoctorIdAndBussType(doctorId, content, bussType);
        for (Integer searchId : searchIds) {
            this.remove(searchId);
        }
        return true;
    }

    @DAOMethod(sql = "select searchId from SearchContent where doctorId=:doctorId and bussType=:bussType and status = 1")
    public abstract List<Integer> findSearchIdsByDoctorIdAndBussType(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("bussType") int bussType);

    @DAOMethod(sql = "select searchId from SearchContent where mpiId=:mpiId and bussType=:bussType and status = 1")
    public abstract List<Integer> findSearchIdsByMpiIdAndBussType(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("bussType") int bussType);

    /**
     * 清空历史搜索记录-医生端
     *
     * @param doctorId 医生内码
     * @param bussType 搜索业务类型
     * @return Boolean
     */
    @RpcService
    public Boolean deleteAllSearchContents(int doctorId, int bussType) {
        List<Integer> searchIds = this.findSearchIdsByDoctorIdAndBussType(doctorId, bussType);
        for (Integer searchId : searchIds) {
            this.remove(searchId);
        }
        return true;
    }

    @DAOMethod(sql = "update SearchContent set status = 0 where searchId=:searchId")
    public abstract void updateStatusById(@DAOParam("searchId") int searchId);

    /**
     * 删除页面上的单条搜索记录 数据库置为无效
     * zhongzx
     * @param doctorId
     * @param content
     * @param bussType
     * @return
     */
    @RpcService
    public Boolean cleanOneSearchContent(int doctorId, String content, int bussType){
        List<Integer> searchIds = this.findSearchIdsByContentAndDoctorIdAndBussType(doctorId, content, bussType);
        for(Integer searchId:searchIds){
            //置成无效
            updateStatusById(searchId);
        }
        return true;
    }

    /**
     * 清空所有的历史记录 医生端
     * @param doctorId
     * @param bussType
     * @return
     */
    @RpcService
    public Boolean cleanAllSearchContent(int doctorId, int bussType){
        List<Integer> searchIds = this.findSearchIdsByDoctorIdAndBussType(doctorId, bussType);
        for (Integer searchId : searchIds) {
            //置成无效
            updateStatusById(searchId);
        }
        return true;
    }

    /**
     * 患者端根据业务类型删除搜索单条记录接口
     * @param mpiId
     * @param content
     * @param bussType
     * @return
     */
    @RpcService
    public Boolean cleanOneSearchContentForPatient(String mpiId, String content, int bussType){
        List<Integer> searchIds = this.findSearchIdsByContentAndMpiIddAndBussType(mpiId, content, bussType);
        for (Integer searchId : searchIds) {
            //置成无效
            updateStatusById(searchId);
        }
        return true;
    }

    /**
     * 患者端根据业务类型删除全部搜索记录接口
     * @param mpiId
     * @param bussType
     * @return
     */
    @RpcService
    public Boolean cleanAllSearchContentForPatient(String mpiId, int bussType){
        List<Integer> searchIds = this.findSearchIdsByMpiIdAndBussType(mpiId, bussType);
        for (Integer searchId : searchIds) {
            //置成无效
            updateStatusById(searchId);
        }
        return true;
    }

}
