package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.Advice;
import eh.entity.bus.QRRecord;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;

public abstract class QRRecordDAO extends HibernateSupportDelegateDAO<QRRecord> {
	public QRRecordDAO() {
		super();
		this.setEntityName(QRRecord.class.getName());
		this.setKeyField("qrRecordId");
	}

	public void saveQrRecord(QRRecord qrRecord){
		qrRecord.setCreateDate(new Date());
		save(qrRecord);
	}

	@DAOMethod(sql = "from QRRecord where recordStatus=1 and qrTypeId=:qrTypeId and qrType=:qrType and " +
			"wxConfigId=:wxConfigId and openId=:openId ")
	public abstract List<QRRecord> findEffectiveRecords(
			@DAOParam("qrTypeId") Integer qrTypeId,
			@DAOParam("qrType") Integer qrType,
			@DAOParam("wxConfigId") Integer wxConfigId,
			@DAOParam("openId") String openId);

	@DAOMethod(sql = "update QRRecord set recordStatus=0 where wxConfigId=:wxConfigId and openId=:openId ")
	public abstract void updateRecordStatusByWxConfigIdAndOpenId(
			@DAOParam("wxConfigId") Integer wxConfigId,
			@DAOParam("openId") String openId);

	@DAOMethod(sql = "select count(*) from QRRecord where QRTypeId=:qrTypeId and RecordStatus=1")
	public abstract Long getNumByQRTypeId(@DAOParam("qrTypeId") Integer qrTypeId);

	@DAOMethod(sql = "from QRRecord where recordStatus=1 and qrTypeId=:qrTypeId order by CreateDate ")
	public abstract List<QRRecord> findEffectiveRecordsByQRTypeId(
			@DAOParam("qrTypeId") Integer qrTypeId);
}
