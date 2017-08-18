package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.entity.evaluation.SensitiveWords;
import org.apache.axis.utils.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

/**
 * @author jianghc
 * @create 2016-11-11 13:59
 **/
public abstract class SensitiveWordsOpDAO extends HibernateSupportDelegateDAO<SensitiveWords> {
    private static final Log logger = LogFactory.getLog(SensitiveWordsOpDAO.class);

    public SensitiveWordsOpDAO() {
        super();
        this.setEntityName(SensitiveWords.class.getName());
        this.setKeyField("id");
    }


    @DAOMethod(sql = " from SensitiveWords where sensitiveWord =:sensitiveWord and Status=1")
    public abstract SensitiveWords getBySensitiveWord(@DAOParam("sensitiveWord") String sensitiveWord);

    @RpcService
    public SensitiveWords createOneSensitiveWord(SensitiveWords word) {
        if (word == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "word is required");
        }
        if (word.getSource() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "Source is required");
        }
        if (word.getSensitiveWord() == null || StringUtils.isEmpty(word.getSensitiveWord().trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "SensitiveWord is required");
        }
        if (word.getReplaceWord() == null || StringUtils.isEmpty(word.getReplaceWord().trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "replaceWord is required");
        }
        word.setSensitiveWord(word.getSensitiveWord().trim());
        word.setReplaceWord(word.getReplaceWord().trim());
        word.setId(null);
        word.setStatus(1);//可用
        UserRoleToken urt = UserRoleToken.getCurrent();
        word.setCreaterId(urt.getId());
        word.setCreaterName(urt.getUserName());
        word.setCreateDate(new Date());
        word.setRate(0);
        if(word.getSource()==null){
            word.setSource(0);
        }
        if (this.getBySensitiveWord(word.getSensitiveWord()) != null) {
            throw new DAOException("敏感词【" + word.getSensitiveWord() + "】已存在");
        }
        word = this.save(word);
        BusActionLogService.recordBusinessLog("敏感词管理", word.getId().toString(),
                "SensitiveWords", "添加敏感词" + word.getSensitiveWord() + ",替换词为" + word.getReplaceWord());
        return word;
    }

    @RpcService
    public List<Map> createSensitiveWords(List<SensitiveWords> words) {
        if (words == null || words.size() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "words is required");
        }
        List<Map> maps = new ArrayList<Map>();
        for (SensitiveWords word : words) {
            try {
                word.setSource(1);//批量导入
                this.createOneSensitiveWord(word);
            } catch (Exception e) {
                Map<String, Object> map = new HashMap<String, Object>();
                BeanUtils.map(word, map);
                map.put("errMsg", e.toString());
                maps.add(map);
            }
        }
        return maps;
    }


    public  void updateStatus(final String strId){
        HibernateSessionTemplate.instance().execute(
                new HibernateStatelessAction() {
                    public void execute(StatelessSession ss) throws Exception {
                        String hql = "update SensitiveWords  set status=0 where id in (:strId)";
                        Query q = ss.createQuery(hql);
                        q.setString("strId", strId);
                        int num = q.executeUpdate();
                        if (num == 0) {
                            throw new DAOException(609, "删除失败！");
                        }
                    }
                });
    }

    @RpcService
    public void deleteStatus(String strId) {
        if (strId == null || StringUtils.isEmpty(strId.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "strId is required");
        }
        this.updateStatus(strId);
        BusActionLogService.recordBusinessLog("敏感词管理", strId,
                "SensitiveWords", "删除敏感词ID为" + strId);
    }


    @RpcService
    @DAOMethod(sql = " from SensitiveWords where status=1 order by id desc")
    public abstract List<SensitiveWords> findAllSensitiveWords(@DAOParam(pageStart = true) int start,
                                                               @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = " select count(*) from SensitiveWords where status=:status")
    public abstract Long getCountByStatus(@DAOParam("status") Integer status);

    @RpcService
    public QueryResult<SensitiveWords> queryAllSensitiveWords( int start,int limit){
        List<SensitiveWords> list = this.findAllSensitiveWords(start,limit);
        if(list==null){
            new QueryResult<SensitiveWords>(this.getCountByStatus(1),start,limit,null);
        }
        return  new QueryResult<SensitiveWords>(this.getCountByStatus(1),start,limit,list);
    }

    @RpcService
    @DAOMethod
    public abstract SensitiveWords getById(Integer id);
}
