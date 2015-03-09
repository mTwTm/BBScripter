
public class BIG5TOOLS {
	public static boolean isHalfWidth(char c)
    {
        return '\u0000' <= c && c <= '\u00FF'
            || '\uFF61' <= c && c <= '\uFFDC'
            || '\uFFE8' <= c && c <= '\uFFEE' ;
    }
	
    public static boolean isBIG5FirstByte(int intVal)
    {
    	if(intVal>=0x81&&0xfe>=intVal)
    		return true;
    	else
    		return false;
    }
	
}
