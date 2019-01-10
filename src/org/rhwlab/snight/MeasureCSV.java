package org.rhwlab.snight;

import java.io.*;
import java.util.Hashtable;

public class MeasureCSV {

	public Hashtable<String, String> iMeasureHash;
	
	/*
	 * second hashtable will hold the v1.0 data so that if v2.0 fails, it can fallback on 1.0
	 * Date: 08/15/16
	 */
	public Hashtable<String, String> iMeasureHash_v1; 
	
	private String iFilePath;
//	private int iGoodLinesRead;

	private static int AuxInfo_v = 1;
	
	/**
	 * Revision added 7/19/16 by @author Braden Katzman
	 * 
	 * With addition of second type of AuxInfo, which contains two vectors in any direction
	 * which define the orientation of the embryo, we need to check which form the AuxInfo
	 * file is and percolate the flag through program execution
	 */
	public MeasureCSV(String filepath) {
		newLine();

		boolean auxInfoV1_opened = false;
		try {
			// initially, try to read AuxInfo file version 2.0
			readAuxInfoV2(filepath);
		} catch(IOException ioe) {
			println("MeasureCSV File I/O Exception opening AuxInfo file version 2.0, trying version 1.0");
			
			// attempt opening the original file version 1.0
			try {
				readAuxInfoV1(filepath, false);
				auxInfoV1_opened = true;
			} catch(IOException e) {
				println("MeasureCSV File I/O Exception opening AuxInfo file version 1.0");
                auxInfoV1_opened = true; // do this just so that it won't run the same code below again
			}
		}

		// WHILE V2 IS STILL EXPERIMENTAL:
        // open up v1 as a fallback
		 /*
			 * Read the data from AuxInfo version 1.0 in addition to 2.0 in case there is failure in 2.0 scheme
                * Date implemented: 08/16/16
                *
			 * When 2.0 becomes the standard, this should be removed for optimization purposes
			 */
		 if (!auxInfoV1_opened) {
             try {
                 System.out.println("Reading AuxInfo version 1 as backup to v2");
                 readAuxInfoV1(filepath, true);
             } catch (IOException e) {
                 println("MeasureCSV File I/O Exception opening AuxInfo file version 1.0");
             }
         }


	}
	
	/**
	 * Read the AuxInfo data into memory
	 * Version 2.0
	 * 
	 * @param filepath - the base file path, without AuxInfo.csv extension appended
	 * @throws IOException
	 */
	private void readAuxInfoV2(String filepath) throws IOException {
		iMeasureHash = new Hashtable<>();


		iFilePath = filepath;
		boolean namesRead = false;
		String [] names = null;
		
		String AuxInfo_v2 = filepath + v2_file_ext;
		
		FileInputStream fis = new FileInputStream(AuxInfo_v2);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		
		if (br.ready()) {
			String first_line = br.readLine();
			System.out.println(first_line);
			
			populateHash(V2, false);
			AuxInfo_v = 2;
			
			// make sure reader is still valid
			if (br.ready()) {
				if (first_line.length() < 2) { 
					br.close();
					return;
				}
				if (!namesRead) {
					names = first_line.split(C);
					namesRead = true;
				}
				// move to the next line
				String s = br.readLine();
				if (s != null) {
					String [] values = s.split(C);
					for (int i=0; i < values.length; i++) {
//						System.out.println("V2: " + names[i] + values[i]);
						iMeasureHash.put(names[i], values[i]);
					}
				}
			}
		}
		
		br.close();
	}
	
	/**
	 * Read the AuxInfo data into memory
	 * Version 1.0
	 * 
	 * @param filepath - the base file path, without AuxInfo.csv extension appended
	 * @param backup - if true, use the backup hashtable to store data because it's being used if version 2.0 fails. if false, use primary hash
	 * @throws IOException
	 */
	private void readAuxInfoV1(String filepath, boolean backup) throws IOException {
		iFilePath = filepath;
		boolean namesRead = false;
		String [] names = null;
		
		if (backup) {
			iMeasureHash_v1 = new Hashtable<String, String>();
		} else {
			iMeasureHash = new Hashtable<String, String>();
		}
		
		
		String AuxInfo_v1 = filepath + v1_file_ext;
		println("MeasureCSV trying to open AuxInfo file: " + AuxInfo_v1);
		namesRead = false;
		names = null;
			
		
		FileInputStream fis = new FileInputStream(AuxInfo_v1);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			
		if (br.ready()) {
			String first_line = br.readLine();
				
			populateHash(V1, true);
				
			if (br.ready()) {
				if (first_line.length() < 2) {
					br.close();
					return;
				}
				if (!namesRead) {
					names = first_line.split(C);
					namesRead = true;
				}
					
				String s = br.readLine();
				if (s != null) {
					String [] values = s.split(C);
					for (int i=0; i < values.length; i++) {
//						System.out.println("V1: " + names[i] + values[i]);
						if (backup) {
							iMeasureHash_v1.put(names[i], values[i]);
						} else {
							iMeasureHash.put(names[i], values[i]);
						}
					}
				}
			}	
		}
		
		br.close();
	}
	
	/**
	 * Populate the hash table of .csv data
	 * 
	 * @param version - indicates which version of AuxInfo file is being used. V1 or V2
	 * @param backup - if true, use the backup hashtable to store data because it's being used if version 2.0 fails. if false, use primary hash
	 */
	private void populateHash(String version, boolean backup) {
		// check if the hash is already initialized, because this method is called multiple times
		if (iMeasureHash == null) {
			iMeasureHash = new Hashtable<String, String>(); 
		}
		
		// remove this after 2.0 becomes standard
		if (iMeasureHash_v1 == null) {
			iMeasureHash_v1 = new Hashtable<String, String>();
		}
		
		if (version.equals(V1)) {
			for (int i=0; i < att_v1.length; i++) {
				if (backup) {
					iMeasureHash_v1.put(att_v1[i], "");
				} else {
					iMeasureHash.put(att_v1[i], "");
				}
				
			}
		} else if (version.equals(V2)) {
			for (int i=0; i < att_v2.length; i++) {
				iMeasureHash.put(att_v2[i], "");
				iMeasureHash_v1.put(att_v1[i], "");
			}
		}
	}

	public void isMeasured() {
		if (isAuxInfoV2()) {
			for (int i=0; i < att_v2.length - 2; i++) {
	    		String value = iMeasureHash.get(att_v2[i]);
	    		if (value.length() == 0) return;
	    	}	
		} else {
			for (int i=0; i < att_v1.length - 2; i++) {
	    		String value = iMeasureHash.get(att_v1[i]);
	    		if (value.length() == 0) return;
	    	}
		}
    	
	}

	public void put(String item, String value) {
		if (item != null && value != null) {
			iMeasureHash.put(item, value);
		}
	}

	public String get(String item) {
		if (item != null) {
			return iMeasureHash.get(item);
		} else {
			return "";
		}
	}

	@Override
	public String toString() {
		String r = get("name");
		if (isAuxInfoV2()) {
			for (int i=1; i < att_v2.length; i++) {
				r += C + get(att_v2[i]);
			}
		} else {
			for (int i=1; i < att_v1.length; i++) {
				r += C + get(att_v1[i]);
			}
		}
		
		return "MeasureCSV: " + r;
	}

	public void writeCSV() {
		writeCSV(iFilePath);
	}

    private void writeCSV(String filePath) {
    	/*
        int k = filePath.lastIndexOf("/");
        String s = "";
        String series = (String)iMeasureHash.get(att[SERIES]);
        if (k == filePath.length() - 1) s = filePath + series;
        else s = filePath + "/" + series;
        s += "AuxInfo.csv";
        //println("writeCSV: " + s);
         */
        PrintWriter pw = null;
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            pw = new PrintWriter(fos);
        } catch(IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
        if (isAuxInfoV2()) {
        	StringBuffer sb = new StringBuffer(att_v2[0]);
            for (int i=1; i < att_v2.length; i++) {
            	sb.append(C + att_v2[i]);
            }
            pw.println(sb.toString());
            sb = new StringBuffer(iMeasureHash.get(att_v2[0]));
            for (int i=1; i < att_v2.length; i++) {
            	sb.append(C + iMeasureHash.get(att_v2[i]));
            }
            pw.println(sb.toString());
        } else {
        	StringBuffer sb = new StringBuffer(att_v1[0]);
            for (int i=1; i < att_v1.length; i++) {
            	sb.append(C + att_v1[i]);
            }
            pw.println(sb.toString());
            sb = new StringBuffer(iMeasureHash.get(att_v1[0]));
            for (int i=1; i < att_v1.length; i++) {
            	sb.append(C + iMeasureHash.get(att_v1[i]));
            }
            pw.println(sb.toString());
        }       
        pw.close();
    }

    public static String [] att_v1 = {
    		"name"
    	   	,"slope"
    	   	,"intercept"
    	   	,"xc"
    	   	,"yc"
    	   	,"maj"
    	   	,"min"
    	   	,"ang"
    	   	,"zc"
    	   	,"zslope"
    	   	,"time"
    	   	,"zpixres"
    	   	,"axis"

    	   };

    	    // revised 20090701
    	    public static String [] defaultAtt_v1 = {
    	    	"xxxx"
    	    	,"0.9"
    	    	,"-27"
    	    	,"360"
    	    	,"255"
    	    	,"585"
    	    	,"390"
    	    	,"0"
    	    	,"14"
    	    	,"10.4"
    	    	,"160"
    	    	,"11.1"
    	    	,"XXX"
    	    };


    	    public static final int
    	    SERIES_v1 = 0
    	   ,TSLOPE_v1 = 1
    	   ,TINTERCEPT_v1 = 2
    	   ,EXCENTER_v1 = 3
    	   ,EYCENTER_v1 = 4
    	   ,EMAJOR_v1 = 5
    	   ,EMINOR_v1 = 6
    	   ,EANG_v1 = 7
    	   ,ZCENTER_v1 = 8
    	   ,ZSLOPE_v1 = 9
    	   ,TIME_v1 = 10
    	   ,ZPIXRES_v1 = 11
    	   ,AXIS_v1 = 12
    	   ;

    public static String [] att_v2 = {
    	   	 "name"
    	   	,"slope"
    	   	,"intercept"
    	   	,"xc"
    	   	,"yc"
    	   	,"maj"
    	   	,"min"
    	   	,"zc"
    	   	,"zslope"
    	   	,"time"
    	   	,"zpixres"
    	   	,"AP_orientation"
    	   	,"LR_orientation"
    	   };

    // revised 20090701
    public static String [] defaultAtt_v2 = {
    		"xxxx"
    	    ,"0.9"
    	   	,"-27"
    	   	,"360"
    	   	,"255"
    	   	,"585"
        	,"390"
   	    	,"14"
   	    	,"10.4"
   	    	,"160"
   	    	,"11.1"
    	   	,"XXX"
    };


    public static final int
    	SERIES_v2 = 0
    	,TSLOPE_v2 = 1
    	,TINTERCEPT_v2 = 2
    	,EXCENTER_v2 = 3
    	,EYCENTER_v2 = 4
    	,EMAJOR_v2 = 5
    	,EMINOR_v2 = 6
    	,ZCENTER_v2 = 7
    	,ZSLOPE_v2 = 8
    	,TIME_v2 = 9
    	,ZPIXRES_v2 = 10
    	,AP_ORIENTATION = 11
    	,LR_ORIENTATION = 12
    ;



    public void checkHash() {
    	if (isAuxInfoV2()) {
    		for (int i=0; i < att_v2.length; i++) {
        		String value = iMeasureHash.get(att_v2[i]);
        		println("checkHash, " + att_v2[i] + CS + value);
        	}
    	} else {
    		for (int i=0; i < att_v1.length; i++) {
        		String value = iMeasureHash.get(att_v1[i]);
        		println("checkHash, " + att_v1[i] + CS + value);
        	}
    	}
    	
    }
    
    public static boolean isAuxInfoV2() {
    	if (AuxInfo_v == 1) {
    		return false;
    	} else if (AuxInfo_v == 2) {
    		return true;
    	}
    	
    	return false;
    }

	private static void println(String s) {System.out.println(s);}
	private void newLine() {System.out.println("");}
    private static final String CS = ", ", C = ",";

    private static final String V1 = "V1";
    private static final String V2 = "V2";
    private static final String v1_file_ext = "AuxInfo.csv";
    private static final String v2_file_ext = "AuxInfo_v2.csv";
//    private static final String AuxInfo_v1_header = "name,slope,intercept,xc,yc,maj,min,ang,zc,zslope,time,zpixres,axis";
//    private static final String AuxInfo_v2_header = "name,slope,intercept,xc,yc,maj,min,ang,zc,zslope,time,zpixres,AP_orientation,LR_orientation";
}
