package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.entity.opauth.UserRoleExtension;
import eh.op.dao.UserRoleExtensionDAO;

public class UserRoleExtensionService {

    @RpcService
    public UserRoleExtension getByUserRoleId(Integer userRoleId) {
        if (userRoleId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userRoleId is null");
        }
        UserRoleExtensionDAO dao = DAOFactory.getDAO(UserRoleExtensionDAO.class);
        UserRoleExtension extension = dao.getByUserRoleId(userRoleId);
        if (extension == null) {
            extension = new UserRoleExtension();
            extension.setUserRoleId(userRoleId);
        }else{
            extension.setReportUserPassword(encryptAndDecryptStr(extension.getReportUserPassword()));
        }
        return extension;
    }

    @RpcService
    public void saveOrUpdateExtension(UserRoleExtension extension) {
        if (extension == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "extension is null");
        }
        if (extension.getUserRoleId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userRoleId is null");
        }
        UserRoleExtensionDAO dao = DAOFactory.getDAO(UserRoleExtensionDAO.class);
        UserRoleExtension target = dao.getByUserRoleId(extension.getUserRoleId());
        if (target == null) {
            extension.setReportUserPassword(encryptAndDecryptStr(extension.getReportUserPassword()));
            dao.save(extension);
        } else {
            Integer id = target.getId();
            BeanUtils.map(extension, target);
            target.setId(id); // Dont change id
            target.setReportUserPassword(encryptAndDecryptStr(target.getReportUserPassword()));
            dao.update(target);
        }
    }
    private String encryptAndDecryptStr(String str) {
        char[] array = str.toCharArray();
        for (int i = 0; i < array.length; i++)//遍历字符数组
        {
            array[i] = (char) (array[i] ^ 1);// 对每个数组元素进行异或运算
        }
        return new String(array);
    }

}
