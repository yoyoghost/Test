package eh.util;

import com.aliyun.openservices.shade.com.alibaba.rocketmq.shade.io.netty.util.internal.StringUtil;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

/**
 * Created by andywang on 2017/6/19.
 */
public class DBUtil {

    private static final Log logger = LogFactory.getLog(DBUtil.class);

    public static String backupTable(final String tableName, final String whereCondition)
    {
        if (StringUtil.isNullOrEmpty(tableName))
        {
            return "";
        }
        final String backTableName = tableName + "_SYSBACK_" +  System.currentTimeMillis();
        logger.info("Start Backup Table - oldTable: " + tableName + " NewTable: " + backTableName + " TimeStamp: " + System.currentTimeMillis() );
        final HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" create table  ").append(backTableName).append(" select * from ").append(tableName);
                if (!StringUtil.isNullOrEmpty(whereCondition))
                {
                    hql.append(" where ").append(whereCondition);
                }
                Query q = ss.createSQLQuery(hql.toString());
                q.executeUpdate();
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        logger.info("Finished Backup Table - oldTable: " + tableName + " NewTable: " + backTableName + " TimeStamp: " + System.currentTimeMillis() );
        return backTableName;
    }

    public static String backupTableByClass(Class cls, String whereCondition)
    {
        String tableName = HibernateToolsUtil.getTableName(cls);
        if (StringUtil.isNullOrEmpty(tableName))
        {
            return "";
        }
       return DBUtil.backupTable(tableName,whereCondition);
    }
}
