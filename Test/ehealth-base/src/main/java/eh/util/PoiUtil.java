package eh.util;

import org.apache.axis.utils.StringUtils;
import org.apache.poi.ss.usermodel.Cell;

/**
 * @author jianghc
 * @create 2016-11-23 13:44
 **/
public class PoiUtil {

    /**
     * 获取单元格值（字符串）
     * @param cell
     * @return
     */
    public static String getStrFromCell(Cell cell){
        if(cell==null){
           return null;
        }
        String strCell =cell.getStringCellValue();
        if(strCell!=null){
            strCell = strCell.trim();
            if(StringUtils.isEmpty(strCell)){
                strCell=null;
            }
        }
        return strCell ;
    }


}
