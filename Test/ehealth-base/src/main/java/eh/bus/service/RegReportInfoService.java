package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import ctd.util.xml.XMLHelper;
import eh.base.dao.OrganDAO;
import eh.bus.dao.CheckReqMsg;
import eh.bus.dao.CheckRequestDAO;
import eh.cdr.dao.DocIndexDAO;
import eh.entity.bus.CheckRequest;
import eh.entity.cdr.DocIndex;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class RegReportInfoService {
	private static final Log logger = LogFactory.getLog(RegReportInfoService.class);

	/**
	 * Title:根据传入的xml串保存电子病历索引记录
	 *
	 * @author zhangjr
	 * @date 2015-10-27
	 * @param inputXml
	 * @return String
	 */
	@RpcService
	public String regReportInfo(String inputXml) {
		logger.info("影像注册参数--->"+inputXml);
		Document responseDocument = createResponseDoc();
		if (StringUtils.isEmpty(inputXml)) {
			// 确认标志,大写T成功大写F失败,失败的时候要填写描述
			responseDocument.getRootElement().element("parameter").element("Confirm").setText("F");
			responseDocument.getRootElement().element("parameter").element("ErrorDesc").setText("入参不能为空!");
			return responseDocument.asXML();
		} else {
			PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
			Document document = null;
			try {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				document = DocumentHelper.parseText(inputXml);
				Element requestElement = document.getRootElement().element("Request");
				String cardType = requestElement.elementText("CardType");//卡类型
				String patientType = requestElement.elementText("PatientType");// 病人类型
				String cardNo = requestElement.elementText("CardNo");// 卡号
				// String labelID = requestElement.elementText("LabelID");//条码号
				String patName = requestElement.elementText("PatName");// 病人姓名
				// String patNameSpell =
				// requestElement.elementText("PatNameSpell");//姓名首拼码
				String iDNum = requestElement.elementText("IDNum");// 身份证号
				// String hospNo = requestElement.elementText("HospNo");//住院号
				String sex = requestElement.elementText("Sex");// 性别
				String birthDay = requestElement.elementText("BirthDay");// 出生日期 格式yyyy-MM-dd
				// String age = requestElement.elementText("Age");//年龄
				String phoneNum = requestElement.elementText("PhoneNum");// 手机号码
				// String applyDeptName =
				// requestElement.elementText("ApplyDeptName");//申请科室
				// String wardName =
				// requestElement.elementText("WardName");//病区名称
				// String bedNo = requestElement.elementText("BedNo");//床号
				// String wardOrReg =
				// requestElement.elementText("WardOrReg");//病人来源
				String reportNo = requestElement.elementText("ReportNo");// 报告唯一号
				String hospCode = requestElement.elementText("HospCode");// 机构代码
				String reportDepart = requestElement.elementText("ReportDepart");// 检查科室名称
				String subSysCode = requestElement.elementText("SubSysCode");// 子系统代码
				String reportDesc = requestElement.elementText("ReportDesc");// 报告类别
				String studyItem = requestElement.elementText("StudyItem");// 检查项目
				String techNo = requestElement.elementText("TechNo");//checkRequest 表 reportID 影像号

				OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
				int organId = 0;
				// 根据组织代码获取机构id
				try {
					if (!StringUtils.isEmpty(hospCode)) {
						organId = organDAO.getOrganIdByOrganizeCode(hospCode);// hospcode转化为组织代码
					}
				} catch (NullPointerException e) {
					// TODO: handle exception
					logger.error("组织代码不存在!");
					responseDocument.getRootElement().element("parameter").element("Confirm").setText("F");
					responseDocument.getRootElement().element("parameter").element("ErrorDesc").setText("组织代码不存在!");
					return responseDocument.asXML();
				}


				String reportDoctorName = requestElement.elementText("ReportDoctorName");// 报告医生名称
				String reportTime = requestElement.elementText("ReportTime");// 报告时间格式yyyy-MM-dd HH:mm:ss
				// String verifyDoctorName =
				// requestElement.elementText("VerifyDoctorName");//审核医生名称
				// String auditingTime =
				// requestElement.elementText("AuditingTime");//审核时间
				// String studyMethod =
				// requestElement.elementText("StudyMethod");//检查方法
				// String studyObservation =
				// requestElement.elementText("StudyObservation");//检查所见
				String studyResult = requestElement.elementText("StudyResult");// 检查结论

				if(StringUtils.isEmpty(cardType)){
					responseDocument.getRootElement().element("parameter").element("Confirm").setText("F");
					responseDocument.getRootElement().element("parameter").element("ErrorDesc").setText("卡类型不能为空!");
					return responseDocument.asXML();
				}
				//更新医技记录 和 影像号
				updateCheckrequest(reportNo,techNo,organId);


				if(StringUtils.isEmpty(patientType)){//如果为空则等于cardType
					patientType = cardType;
				}
				Patient patient = null;
				String mapiId = null;
				Date nowTime = new Date();
				if (!StringUtils.isEmpty(iDNum)) {// 如果身份证不为空
					patient = patientDAO.getByRawIdCard(iDNum);
					// 再根据病人类型及卡号查询
					if (patient == null) {
						if (!StringUtils.isEmpty(patientType)&& !StringUtils.isEmpty(cardNo)) {// 如果病人（卡）类型 及卡号都不为空
							if("1".equals(cardType)){//根据卡号和 发卡机构查询
								patient = patientDAO.getByCardNoAndOrganId(cardNo, organId);
							}else if("2".equals(cardType)){
								patient = patientDAO.getByCardNoAndPatientType(cardNo, patientType);
							}
						}
					}else{
						mapiId = patient.getMpiId();
					}
				} else {
					// 身份证号、病人类型及卡号都不存在
						responseDocument.getRootElement().element("parameter").element("Confirm").setText("F");
						responseDocument.getRootElement().element("parameter").element("ErrorDesc").setText("身份证为空，无法创建病人主索引!");
						return responseDocument.asXML();

				}

				if (patient == null && !ValidateUtil.blankString(phoneNum)) {// 新建病人主索引  手机号不能为空  为空则跳过
					patient = new Patient();
					patient.setPatientName(patName);
					patient.setPatientSex(sex);
					patient.setBirthday(StringUtils.isEmpty(birthDay) ? new Date(): sdf.parse(birthDay));
					patient.setPatientType(patientType);
					patient.setIdcard(iDNum);
					patient.setMobile(phoneNum);
					Patient resultPatient = patientDAO.getOrUpdate(patient);
					mapiId = resultPatient.getMpiId();
				}

				if(!ValidateUtil.blankString(mapiId)){
					//查询报告是否已经添加
					DocIndexDAO dao = DAOFactory.getDAO(DocIndexDAO.class);
					List<DocIndex> docs = dao.findByMpiidAndOrganDocId(mapiId,reportNo);
					if(docs == null || docs.size()==0 ){
						// 保存电子病历索引记录
						DocIndex docIndex = new DocIndex();
						docIndex.setMpiid(mapiId);
						docIndex.setDocClass(98);
						docIndex.setDocTitle(studyItem);
						docIndex.setOrganDocId(reportNo);
						docIndex.setDocSummary(studyResult);
						docIndex.setCreateOrgan(organId);
						docIndex.setDepartName(reportDepart);
						docIndex.setDoctorName(reportDoctorName);
						docIndex.setDoctypeName(reportDesc);
						if ("LIS_SH".equals(subSysCode)) {
							docIndex.setDocType("1");
						} else {
							docIndex.setDocType("2");
						}
						docIndex.setDocStatus(0);
						docIndex.setCreateDate(StringUtils.isEmpty(reportTime) ? nowTime : sdf2.parse(reportTime));
						dao.saveDocIndex(docIndex);
					}
				}
				// 确认标志置为T，标志成功
				responseDocument.getRootElement().element("parameter").element("Confirm").setText("T");
			} catch (DocumentException e) {
				logger.error("regReportInfo() error : "+e);
				logger.error("入参为非法的xml串!");
				responseDocument.getRootElement().element("parameter").element("Confirm").setText("F");
				responseDocument.getRootElement().element("parameter").element("ErrorDesc").setText("入参为非法的xml串!");
				return responseDocument.asXML();
			} catch (ParseException e) {
				logger.error("regReportInfo() error : "+e);
				logger.error("入参中时间格式有误!");

				responseDocument.getRootElement().element("parameter").element("Confirm").setText("F");
				responseDocument.getRootElement().element("parameter").element("ErrorDesc").setText("入参中时间格式有误!");
				return responseDocument.asXML();
			}
		}

		return responseDocument.asXML();
	}

	private void updateCheckrequest(String reportNo, String techNo, int organId) {

		//将OrganDocID 会写到 checkRequest表中
		CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
		checkRequestDAO.updateOrganDocIDByReportIdAndOrganId(reportNo, techNo, organId);
		CheckRequest checkRequest = checkRequestDAO.getCheckRequestByOrganIdAndReportId(organId,techNo);
		if(checkRequest != null){
//			CheckReqMsg.sendMsg(checkRequest.getCheckRequestId(), "checkRequestReportIssue");
			SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
			smsPushService.pushMsgData2Ons(checkRequest.getCheckRequestId(), checkRequest.getOrganId(), "CheckReqForReport", "CheckReqForReport", null);
			CheckReqMsg.checkSysMsgAndPush(checkRequest.getCheckRequestId(),3);
		}
	}

	/**
	 * Title:创建返回xml
	 *
	 * @author zhangjr
	 * @date 2015-10-27
	 * @return Document
	 */
	private Document createResponseDoc() {
		Document responseDocument = XMLHelper.createDocument();
		Element root = responseDocument.addElement("root");
		Element parameter = root.addElement("parameter");
		parameter.addElement("Confirm");
		parameter.addElement("ErrorDesc");
		return responseDocument;
	}
}
