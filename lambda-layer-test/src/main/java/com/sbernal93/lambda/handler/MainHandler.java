package com.sbernal93.lambda.handler;

import java.util.HashMap;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sbernal93.layer.LayerUtil;

/**
 * Created on: Aug 12, 2019
 * @author santiagobernal
 */
public class MainHandler implements RequestHandler<HashMap<String, Object>, Object>{

    public Object handleRequest(HashMap<String, Object> input, Context context) {
        System.out.println("Lambda invoked, using layer function");
        LayerUtil.printMethod("test");
        return null;
    }

}
