package eh.cdr.service;

import ctd.util.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/5/24.
 */
public class RecipeBaseService {

    /** logger */
    private static final Logger logger = LoggerFactory.getLogger(RecipeBaseService.class);


    public String logErrorResult(String method, Object obj){
        String objStr = JSONUtils.toString(obj);
        logger.error(method+" result={}",objStr);
        return objStr;
    }

}
