package eh.evaluation.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.evaluation.SensitiveWords;

import java.util.List;

/**
 * Created by fql
 * 敏感词过滤对应DAO
 */
public abstract class SensitiveWordsDAO extends HibernateSupportDelegateDAO<SensitiveWords> {
    public SensitiveWordsDAO() {
        super();
        this.setEntityName(SensitiveWords.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据词汇查找记录
     *
     * @param sensitiveWord
     * @return
     */
    @DAOMethod(sql = "from SensitiveWords where sensitiveWord=:sensitiveWord and status=1", limit = 0)
    public abstract List<SensitiveWords> findBySensitiveWords(@DAOParam("sensitiveWord") String sensitiveWord);

    /**
     * 查询所有status为1的有效敏感词汇实体
     *
     * @return
     */
    @DAOMethod(sql = "from SensitiveWords where status=1", limit = 0)
    public abstract List<SensitiveWords> findAll();

    /**
     * 查询所有status为1的有效敏感词汇
     *
     * @return
     */
    @DAOMethod(sql = "select sensitiveWord from SensitiveWords where status=1", limit = 0)
    public abstract List<String> findAllWords();

    /**
     * 根据敏感词更新其使用频率
     *
     * @param rate
     * @return
     */
    @DAOMethod(sql = "update SensitiveWords set rate=:rate where sensitiveWord=:sensitiveWord and status=1")
    public abstract void updateRateByWords(@DAOParam("rate") int rate, @DAOParam("sensitiveWord") String sensitiveWord);

}
