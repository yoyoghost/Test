package eh.bus.excel;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserRoleTokenEntity;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.bus.dao.HospitalDataDAO;
import eh.bus.dao.HospitalUserDAO;
import eh.entity.bus.HospitalData;
import eh.entity.bus.HospitalUser;
import eh.entity.mpi.FamilyMember;
import eh.entity.mpi.Patient;
import eh.mpi.constant.FamilyMemberConstant;
import eh.mpi.constant.PatientConstant;
import eh.mpi.dao.FamilyMemberDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.service.FamilyMemberService;
import eh.util.ChinaIDNumberUtil;
import eh.utils.DateConversion;
import eh.wxpay.util.RandomStringGenerator;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2017/7/1 0001.
 */
public class HospitalDataService {
    private static final Logger log = LoggerFactory.getLogger(HospitalDataService.class);
    private HospitalDataDAO hospitalDataDao;
    private HospitalUserDAO hospitalUserDao;

    public HospitalDataService() {
        hospitalDataDao = DAOFactory.getDAO(HospitalDataDAO.class);
        hospitalUserDao = DAOFactory.getDAO(HospitalUserDAO.class);
    }

    @RpcService
    public void importExcelAndProcessData(String fileUrl, String suffix) throws IOException, URISyntaxException {
        // save file on local
        File file = saveOnLocal(fileUrl, suffix);
        // import data into DB
        Date startImportTime = new Date();
        int excelRecordCount = 0;
        log.info("start fetch and read excel with time[{}]", startImportTime);
        if (suffix.toLowerCase().equals("csv")) {
            excelRecordCount = parseAndImportCsv(file, startImportTime);
        } else {
            excelRecordCount = parseAndImportBigFile(file, startImportTime);
        }
        log.info("success process [{}] excel records", excelRecordCount);
        // filter and remove repeat data
        filterRepeatDataAndImportIntoHospitalUser(startImportTime);
        // process data
        log.info("start process hospitalUsers");
        processData();
        log.info("success process hospitalUsers");
    }

    private File saveOnLocal(String fileUrl, String suffix) throws URISyntaxException, IOException {
        try {
            String classRoot = new File(HospitalDataService.class.getClassLoader().getResource("/").toURI()).getPath();
            File tmpFileDir = new File(classRoot + File.separator + "tmp");
            tmpFileDir.mkdirs();
            File tmpFile = new File(tmpFileDir.getPath() + File.separator + System.currentTimeMillis() + "." + suffix);
            DataOutputStream out = new DataOutputStream(new FileOutputStream(tmpFile));
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20 * 60 * 1000);
            DataInputStream in = new DataInputStream(connection.getInputStream());
            byte[] buffer = new byte[4096];
            int count = 0;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            out.close();
            in.close();
            return tmpFile;
        } catch (Exception e) {
            log.error("saveOnLocal failed, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return null;
    }

    private void filterRepeatDataAndImportIntoHospitalUser(Date startImportTime) {
        Date anHourAgo = DateConversion.getDateAftHour(startImportTime, -1);
        Long count = hospitalDataDao.getHospitalDataCount(anHourAgo);
        log.info("start copy data into hospitalUser count[{}]", count);
        int size = 100;
        for (int i = 0; i < count / size; i++) {
            int start = i * size;
            List<HospitalData> hospitalDataList = hospitalDataDao.findHospitalData(anHourAgo, start, size);
            hospitalUserDao.batchSaveHospitalData(hospitalDataList);
        }
        List<HospitalData> hospitalDataList = hospitalDataDao.findHospitalData(anHourAgo, 0, size);
        hospitalUserDao.batchSaveHospitalData(hospitalDataList);
        log.info("success copy [{}] records into hospitalUser", count);
    }

    @RpcService
    public void processData() {
        List<HospitalUser> hospitalUserList = hospitalUserDao.findUnProcessHospitalUser();
        while (hospitalUserList.size() > 0) {
            processHospitalUserList(hospitalUserList);
            hospitalUserList = hospitalUserDao.findUnProcessHospitalUser();
        }
    }

    private void processHospitalUserList(List<HospitalUser> hospitalUserList) {
        for (HospitalUser hd : hospitalUserList) {
            try {
                processForPlatUser(hd);
            } catch (Exception e) {
                log.error("processHospitalUserList the hd[{}] exception, errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(hd), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }
    }

    private void processForPlatUser(final HospitalUser hd) {
        AbstractHibernateStatelessResultAction<HospitalData> action = new AbstractHibernateStatelessResultAction<HospitalData>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                try {
                    ChinaIDNumberUtil.convert15To18(hd.getSfzh().toUpperCase());
                    OAuthWeixinMPDAO oauthWeixinMpDao = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
                    String appId = "wxa4432faba935a4b8";
                    OAuthWeixinMP mp = oauthWeixinMpDao.getByAppIdAndOpenId(appId, hd.getWxh());
                    if (mp == null) {
                        User user = processForUser(hd);
                        UserRoleToken urt = processForUrt(user.getId());
                        Patient patient = processForPatient(hd, user.getId());
                        processForWeixinMp(appId, hd, urt);
                        processFamilyMemberForSelf(patient);
                    } else {
                        Patient patient = processForPatient(hd, null);
                        Patient mainUserPatient = DAOFactory.getDAO(PatientDAO.class).getByLoginId(mp.getUserId());
                        processFamilyMember(mainUserPatient.getMpiId(), patient);
                    }
                    hospitalUserDao.updateHospitalUserProcessed(hd.getId(), 1);
                } catch (Exception e) {
                    hospitalUserDao.updateHospitalUserProcessed(hd.getId(), 2);
                    log.error("processForPlatUser error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }

            private void processFamilyMember(String mpiId, Patient patient) {
                FamilyMember member = new FamilyMember();
                member.setRelation(FamilyMemberConstant.MEMBER_RELATION_IMPORT);
                member.setMpiid(mpiId);
                member.setMemberMpi(patient.getMpiId());
                member.setCreateDt(new Date());
                member.setOrganId(1000899);
                member.setIsolationFlag(FamilyMemberConstant.ISOLATION_YES);
                FamilyMemberDAO familyMemberDao = DAOFactory.getDAO(FamilyMemberDAO.class);
                familyMemberDao.save(member);
            }

            private void processFamilyMemberForSelf(Patient patient) {
                FamilyMemberDAO memberDAO=DAOFactory.getDAO(FamilyMemberDAO.class);
                FamilyMember familyMember = memberDAO.getByMpiIdAndMemberMpi(
                        patient.getMpiId(), patient.getMpiId());
                FamilyMember returnMember=null;
                if (familyMember == null) {
                    //新增关系
                    FamilyMember member=new FamilyMember();
                    member.setMpiid(patient.getMpiId());
                    member.setMemberMpi(patient.getMpiId());
                    member.setOrganId(1000899);
                    member.setIsolationFlag(FamilyMemberConstant.ISOLATION_YES);
                    member.setRelation(FamilyMemberConstant.MEMBER_RELATION_SELF);
                    returnMember= memberDAO.save(member);
                }
            }

            private Patient processForPatient(HospitalUser hd, String uid) {
                PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
                Patient idPatient = patientDao.getByIdCard(hd.getSfzh());
                Patient p = new Patient();
                p.setPatientName(hd.getHzxm());
                String idCard18;
                try {
                    idCard18 = ChinaIDNumberUtil.convert15To18(hd.getSfzh().toUpperCase());
                    if (idPatient != null) {
                        p.setIdcard2(idCard18);
                    } else {
                        p.setIdcard(idCard18);
                    }
                    p.setRawIdcard(hd.getSfzh());
                    p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                    p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                } catch (ValidateException e) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
                }

                p.setMobile(hd.getLxdh());
                p.setHomeArea("");

                if (p.getBirthday() == null) {
                    p.setBirthday(new Date());
                }
                p.setLoginId(uid);
                p.setCreateDate(new Date());
                p.setLastModify(new Date());
                p.setPatientType("1");// 1：自费
                Boolean guardianFlag = p.getGuardianFlag() == null ? false : p.getGuardianFlag();
                p.setGuardianFlag(guardianFlag);
                p.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
                p.setHealthProfileFlag(p.getHealthProfileFlag() == null ? false : p.getHealthProfileFlag());
                p.setPatientUserType(0);
                return patientDao.save(p);

            }

            private User processForUser(HospitalUser hd) {
                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
                User user = new User();
                user.setId(SystemConstant.SHANGHAI_SIXTH_POPULATION_HOSPITAL_PREFIX + RandomStringGenerator.getRandomStringByLength(10));
                user.setName(hd.getHzxm());
                user.setCreateDt(new Date());
                user.setLastModify(System.currentTimeMillis());
                user.setPlainPassword(hd.getSfzh().substring(hd.getSfzh().length() - 6));
                user.setStatus("1");
                return userDao.save(user);
            }

            private UserRoleToken processForUrt(String uid) {
                UserRoleTokenEntity ure = new UserRoleTokenEntity();
                ure.setRoleId(SystemConstant.ROLES_PATIENT);
                ure.setUserId(uid);
                ure.setTenantId("eh");
                ure.setManageUnit("eh");
                UserRoleTokenDAO userRoleTokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                return userRoleTokenDao.save(ure);
            }

            private void processForWeixinMp(String appId, HospitalUser hd, UserRoleToken urt) {
                OAuthWeixinMPDAO oauthWeixinMpDao = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
                OAuthWeixinMP mp = new OAuthWeixinMP();
                mp.setAppId(appId);
                mp.setOpenId(hd.getWxh());
                mp.setCreateDt(new Date());
                mp.setUserId(urt.getUserId());
                mp.setUrt(urt.getId());
                mp.setLastModify(System.currentTimeMillis());
                mp.setSubscribe("1");//注册的时候默认关注公众号
                oauthWeixinMpDao.save(mp);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    private int parseAndImportCsv(File csvFile, Date startImportTime) {
        try {
            List<HospitalData> hospitalDataList = new ArrayList<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), "GBK"));
            String line = null;
            int times = 1;
            int count = 0, failCount = 0;
            while ((line = br.readLine()) != null) {
                String[] cells = line.split(",");
                if (cells[0].contains("id") || cells.length < 10) {
                    continue;
                }
                try {
                    HospitalData hd = convertArrayToHospitalData(cells);
                    hd.setCreateTime(startImportTime);
                    hospitalDataList.add(hd);
                } catch (Exception e) {
                    failCount++;
                    log.error("exception position[{}], errorMessage[{}], stackTrace[{}]", count, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
                count++;
                if (hospitalDataList.size() >= 1000) {
                    batchInsert(hospitalDataList);
                    hospitalDataList = Lists.newArrayList();
                    log.info("insert hospitalData for the [{}] time", times);
                    times++;
                }
            }
            if (hospitalDataList.size() > 0) {
                batchInsert(hospitalDataList);
            }
            br.close();
            log.info("parseAndImportCsv failCount[{}]", failCount);
            return count;
        } catch (Exception e) {
            log.error("parseAndImportCsv exception, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return 0;
    }

    private HospitalData convertArrayToHospitalData(String[] cells) {
        HospitalData hd = new HospitalData();
        hd.setId(getInt(cells[0]));
        hd.setWxh(cells[1].trim());
        hd.setPatid(getInt(cells[2]));
        hd.setHzxm(cells[3].trim());
        hd.setSfzh(cells[4].trim());
        hd.setLxdh(cells[5].trim());
        hd.setLxdz(cells[6].trim());
        hd.setBlh(cells[7].trim());
        hd.setCardno(cells[8].trim());
        hd.setCardtype(getInt(cells[9]));
        hd.setCjsj(cells[10] == null ? null : DateConversion.parseDate(cells[10].trim(), "yyyy/MM/dd HH:mm"));
        hd.setJlzt(getInt(cells[11]));
        if (cells.length >= 13) {
            hd.setXzzt(getInt(cells[12]));
        }
        return hd;
    }

    private Integer getInt(String value) {
        if (value == null) return null;
        if (value.trim().equals("")) return null;
        return Integer.valueOf(value.trim());
    }

    private int parseAndImportBigFile(File file, Date startImportTime) {
        try {
            Excel2007Reader excel07 = new Excel2007Reader();
            excel07.process(file.getPath());
            List<List<String>> exceldata = excel07.getExcelData();
            List<HospitalData> hospitalDataList = new ArrayList<>();
            int times = 1;
            for (List<String> rowList : exceldata) {
                if (rowList.get(0).contains("id")) {
                    continue;
                }
                HospitalData hd = convertToHospitalData(rowList);
                hospitalDataList.add(hd);
                if (hospitalDataList.size() >= 1000) {
                    batchInsert(hospitalDataList);
                    hospitalDataList = Lists.newArrayList();
                    log.info("insert hospitalData for the [{}] time", times);
                    times++;
                }
            }
            if (hospitalDataList.size() > 0) {
                batchInsert(hospitalDataList);
            }
            return exceldata.size();
        } catch (Exception e) {
            log.error("parseAndImportBigFile exception, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return 0;
    }

    private HospitalData convertToHospitalData(List<String> rowList) {
        HospitalData hd = new HospitalData();
        hd.setId(Integer.valueOf(rowList.get(0)));
        hd.setWxh(rowList.get(1));
        hd.setPatid(Integer.valueOf(rowList.get(2)));
        hd.setHzxm(rowList.get(3));
        hd.setSfzh(rowList.get(4));
        hd.setLxdh(rowList.get(5));
        hd.setLxdz(rowList.get(6));
        hd.setBlh(rowList.get(7));
        hd.setCardno(rowList.get(8));
        hd.setCardtype(Integer.valueOf(rowList.get(9)));
        hd.setCjsj(DateConversion.parseDate(rowList.get(10), "yyyy-MM-dd HH:mm"));
        hd.setJlzt(Integer.valueOf(rowList.get(11)));
        hd.setXzzt(Integer.valueOf(rowList.get(12)));
        return hd;
    }

    private int parseAndImportDataForXlsx(File excelFile, Date startImportTime) {
        URLConnection connection = null;
        InputStream is = null;
        int count = 0;
        try {
            is = new FileInputStream(excelFile);
            Workbook workBook = new XSSFWorkbook(is);
            List<HospitalData> hospitalDataList = new ArrayList<>();
            int times = 1;
            for (int sheetNum = 0; sheetNum < workBook.getNumberOfSheets(); sheetNum++) {
                Sheet sheet = workBook.getSheetAt(sheetNum);
                if (sheet == null) {
                    continue;
                }
                for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row != null) {
                        HospitalData hd = parseRow(row);
                        hd.setCreateTime(startImportTime);
                        hospitalDataList.add(hd);
                        count++;
                    }
                    if (hospitalDataList.size() >= 1000) {
                        batchInsert(hospitalDataList);
                        hospitalDataList = Lists.newArrayList();
                        log.info("insert hospitalData for the [{}] time", times);
                        times++;
                    }
                }
                if (hospitalDataList.size() > 0) {
                    batchInsert(hospitalDataList);
                }
            }
        } catch (IOException e) {
            log.error("IOException when reading file, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } catch (Exception e) {
            log.error("exception when process file, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    log.error("file inputStream close exception!!! errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }
        }
        return count;
    }

    private void batchInsert(List<HospitalData> hospitalDataList) {
        hospitalDataDao.batchSave(hospitalDataList);
    }

    private HospitalData parseRow(Row row) {
        Cell idCell = row.getCell(0);
        Cell wxhCell = row.getCell(1);
        Cell patidCell = row.getCell(2);
        Cell hzxmCell = row.getCell(3);
        Cell sfzhCell = row.getCell(4);
        Cell lxdhCell = row.getCell(5);
        Cell lxdzCell = row.getCell(6);
        Cell blhCell = row.getCell(7);
        Cell cardnoCell = row.getCell(8);
        Cell cardtypeCell = row.getCell(9);
        Cell cjsjCell = row.getCell(10);
        Cell jlztCell = row.getCell(11);
        Cell xzztCell = row.getCell(12);

        HospitalData hd = new HospitalData();
        hd.setId(Integer.valueOf(getCellValue(idCell)));
        hd.setWxh(getCellValue(wxhCell));
        hd.setPatid(Integer.valueOf(getCellValue(patidCell)));
        hd.setHzxm(getCellValue(hzxmCell));
        hd.setSfzh(getCellValue(sfzhCell));
        hd.setLxdh(getCellValue(lxdhCell));
        hd.setLxdz(getCellValue(lxdzCell));
        hd.setBlh(getCellValue(blhCell));
        hd.setCardno(getCellValue(cardnoCell));
        hd.setCardtype(Integer.valueOf(getCellValue(cardtypeCell)));
        hd.setCjsj(DateConversion.parseDate(getCellValue(cjsjCell), "yyyy-MM-dd HH:mm"));
        hd.setJlzt(Integer.valueOf(getCellValue(jlztCell)));
        hd.setXzzt(Integer.valueOf(getCellValue(xzztCell)));
        return hd;
    }

    private int parseAndImportDataForXls(String fileUrl, Date startImportTime) throws IOException {
        URLConnection connection = null;
        InputStream is = null;
        int count = 0;
        try {
            log.info("start download excel[{}]", fileUrl);
            URL urlObject = new URL(fileUrl);
            connection = urlObject.openConnection();
            is = connection.getInputStream();
            HSSFWorkbook workBook = new HSSFWorkbook(is);
            List<HospitalData> hospitalDataList = new ArrayList<>();
            int times = 0;
            for (int sheetNum = 0; sheetNum < workBook.getNumberOfSheets(); sheetNum++) {
                HSSFSheet sheet = workBook.getSheetAt(sheetNum);
                if (sheet == null) {
                    continue;
                }
                for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    HSSFRow row = sheet.getRow(rowNum);
                    if (row != null) {
                        HospitalData hd = parseHSSFRow(row);
                        hd.setCreateTime(startImportTime);
                        hospitalDataList.add(hd);
                        count++;
                    }
                    if (hospitalDataList.size() >= 1000) {
                        batchInsert(hospitalDataList);
                        hospitalDataList = Lists.newArrayList();
                        log.info("insert hospitalData for the [{}] time", times);
                        times++;
                    }
                }
            }
        } catch (IOException e) {
            log.error("IOException when reading file, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } catch (Exception e) {
            log.error("exception when process file, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    log.error("file inputStream close exception!!! errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }
        }
        return count;
    }

    private HospitalData parseHSSFRow(HSSFRow row) {
        HSSFCell idCell = row.getCell(0);
        HSSFCell wxhCell = row.getCell(1);
        HSSFCell patidCell = row.getCell(2);
        HSSFCell hzxmCell = row.getCell(3);
        HSSFCell sfzhCell = row.getCell(4);
        HSSFCell lxdhCell = row.getCell(5);
        HSSFCell lxdzCell = row.getCell(6);
        HSSFCell blhCell = row.getCell(7);
        HSSFCell cardnoCell = row.getCell(8);
        HSSFCell cardtypeCell = row.getCell(9);
        HSSFCell cjsjCell = row.getCell(10);
        HSSFCell jlztCell = row.getCell(11);
        HSSFCell xzztCell = row.getCell(12);

        HospitalData hd = new HospitalData();
        hd.setId(Integer.valueOf(getHSSFCellValue(idCell)));
        hd.setWxh(getHSSFCellValue(wxhCell));
        hd.setPatid(Integer.valueOf(getHSSFCellValue(patidCell)));
        hd.setHzxm(getHSSFCellValue(hzxmCell));
        hd.setSfzh(getHSSFCellValue(sfzhCell));
        hd.setLxdh(getHSSFCellValue(lxdhCell));
        hd.setLxdz(getHSSFCellValue(lxdzCell));
        hd.setBlh(getHSSFCellValue(blhCell));
        hd.setCardno(getHSSFCellValue(cardnoCell));
        hd.setCardtype(Integer.valueOf(getHSSFCellValue(cardtypeCell)));
        hd.setCjsj(DateConversion.parseDate(getHSSFCellValue(cjsjCell), "yyyy-MM-dd HH:mm"));
        hd.setJlzt(Integer.valueOf(getHSSFCellValue(jlztCell)));
        hd.setXzzt(Integer.valueOf(getHSSFCellValue(xzztCell)));
        return hd;
    }

    private String getHSSFCellValue(HSSFCell cell) {
        String result = null;
        if (cell == null) {
            return result;
        }
        switch (cell.getCellType()) {
            case HSSFCell.CELL_TYPE_BLANK:
                result = null;
                break;
            case HSSFCell.CELL_TYPE_NUMERIC:
                cell.setCellType(HSSFCell.CELL_TYPE_STRING);
                result = cell.getStringCellValue();
                break;
            case HSSFCell.CELL_TYPE_STRING:
                result = cell.getStringCellValue();
                break;
        }
        return result;
    }

    private String getCellValue(Cell cell) {
        String result = null;
        if (cell == null) {
            return result;
        }
        switch (cell.getCellType()) {
            case Cell.CELL_TYPE_BLANK:
                result = null;
                break;
            case Cell.CELL_TYPE_NUMERIC:
                cell.setCellType(HSSFCell.CELL_TYPE_STRING);
                result = cell.getStringCellValue();
                break;
            case Cell.CELL_TYPE_STRING:
                result = cell.getStringCellValue();
                break;
        }
        if (result != null) {
            result = result.trim();
        }
        return result;
    }
}
