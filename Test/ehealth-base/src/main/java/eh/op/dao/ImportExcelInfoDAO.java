package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.xls.ImportExcelInfo;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author jianghc
 * @create 2016-11-17 14:59
 **/
public abstract class ImportExcelInfoDAO extends HibernateSupportDelegateDAO<ImportExcelInfo> {
    public ImportExcelInfoDAO(){
        super();
        this.setEntityName(ImportExcelInfo.class.getName());
        this.setKeyField("id");
    }

    public ImportExcelInfo uploadFile(ImportExcelInfo excel){
        if(excel==null){
            throw new DAOException(DAOException.VALUE_NEEDED," excel is required");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        excel.setUploader(urt.getId());
        excel.setUploaderName(urt.getUserName());
        excel.setUploadDate(new Date());
        excel.setStatus(0);
        excel.setSuccess(0);
        return save(excel);
    }

    @DAOMethod
    public abstract ImportExcelInfo getById(Integer id);


    public QueryResult<ImportExcelInfo> queryImportExcelInfo(final Integer excelType,final Date bDate ,final Date eDate,final int start ,final int limit){
        HibernateStatelessResultAction<QueryResult<ImportExcelInfo>> action = new AbstractHibernateStatelessResultAction<QueryResult<ImportExcelInfo>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                StringBuilder hql = new StringBuilder(" from ImportExcelInfo where 1=1 ");
                if(excelType!=null){
                    hql.append(" And excelType =").append(excelType);
                }
                if (bDate!=null){
                    hql.append(" And uploadDate >='").append(sdf.format(bDate)).append(" 00:00:00'");
                }
                if (eDate!=null){
                    hql.append(" And uploadDate <='").append(sdf.format(eDate)).append(" 23:59:59'");
                }
                Query countQuery = ss.createQuery("select count(*) "+hql.toString());
                long total = (long) countQuery.uniqueResult();//获取总条数
                Query query = ss.createQuery(hql.toString()+" order by uploadDate desc");
                query.setMaxResults(limit);
                query.setFirstResult(start);
                List<ImportExcelInfo> list = query.list();
                if(list==null){
                    list = new ArrayList<ImportExcelInfo>();
                }
                setResult(new QueryResult<ImportExcelInfo>(total,start,list.size(),list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }



}
