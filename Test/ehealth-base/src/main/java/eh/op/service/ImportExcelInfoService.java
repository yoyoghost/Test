package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.entity.xls.ImportExcelInfo;
import eh.op.dao.DoctorPointXlsDAO;
import eh.op.dao.ImportExcelInfoDAO;
import eh.op.dao.XlsDoctorInfoDAO;
import eh.op.dao.XlsRelationPatientDAO;

import java.util.Date;

/**
 * @author jianghc
 * @create 2016-12-23 10:16
 **/
public class ImportExcelInfoService {

    @RpcService
    public QueryResult<ImportExcelInfo> queryImportExcelInfo(Integer excelType, Date bDate, Date eDate, int start, int limit) {
        ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
        return importExcelInfoDAO.queryImportExcelInfo(excelType, bDate, eDate, start, limit);
    }

    @RpcService
    public QueryResult<Object> queryExcelDetailByXlsId(Integer xlsId, int start, int limit) {
        ImportExcelInfoDAO importExcelInfoDAO = DAOFactory.getDAO(ImportExcelInfoDAO.class);
        ImportExcelInfo info = importExcelInfoDAO.getById(xlsId);
        if (info == null || info.getExcelType() == null) {
            return null;
        }
        QueryResult<Object> objs = null;

        switch (info.getExcelType()) {
            case 1:
                DoctorPointXlsDAO doctorPointXlsDAO = DAOFactory.getDAO(DoctorPointXlsDAO.class);
                objs = doctorPointXlsDAO.queryDoctorPointXls(xlsId,start,limit);
                break;
            case 2:
                XlsDoctorInfoDAO xlsDoctorInfoDAO  = DAOFactory.getDAO(XlsDoctorInfoDAO.class);
                objs = xlsDoctorInfoDAO.queryDoctorPointXls(xlsId,start,limit);
                break;
            case 3:
                XlsRelationPatientDAO xlsRelationPatientDAO  = DAOFactory.getDAO(XlsRelationPatientDAO.class);
                objs = xlsRelationPatientDAO.queryRelationPatientXls(xlsId,start,limit);
                break;
            default:
                throw new DAOException("ExcelType is not allowed");
        }
        return objs;
    }


}
