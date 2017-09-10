package com.android.volley.toolbox;


import java.io.UnsupportedEncodingException;

/**
 * Created by yang-gichang on 2017. 9. 9..
 */

public class ParsedString{
    private String parsed;

    public ParsedString(String string){
        parsed = string;
    }

    public ParsedString(byte data[],String charset){
        try {
            parsed = new String(data,charset);
        } catch (UnsupportedEncodingException e) {
            parsed = new String(data);
        }
    }

    public void fitContentSizeDynamic(){
        String[] Elements={"img","div"};
        modify(Elements);
    }

    @Override
    public String toString(){
       return parsed;
    }

    private String modify(String[] Elements){
        String[] tmp=parsed.split("<");
        StringBuffer modifiedString=new StringBuffer("");
        for(int i=0;i<tmp.length;i++)
        {
            String token = tmp[i];
            boolean flag=false;
            for(int n=0;n<Elements.length;n++) {
                String Element = Elements[n];

                if (token.contains(Element) && (!token.contains("max-width:"))) {
                    if (token.contains("style=\""))
                        modifiedString.append(token.replace("style=\"", "style=\"max-width:100%;"));
                    else modifiedString.append(token.replace(Element,Element+" style=\"max-width:100%;\""));

                    flag=true;
                    break;
                }
            }

            if(!flag) modifiedString.append(token);

        }
        return modifiedString.toString();
    }
}
