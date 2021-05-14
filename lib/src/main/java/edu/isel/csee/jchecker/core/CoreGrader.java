package edu.isel.csee.jchecker.core;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import edu.isel.csee.jchecker.core.stage.*;
import edu.isel.csee.jchecker.score.*;
import edu.isel.csee.jchecker.statics.*;
import edu.isel.csee.jchecker.statics.utils.EntireContentParser;
import edu.isel.csee.jchecker.statics.utils.MainClassDetector;


public class CoreGrader {
	private boolean flag = false;
	private ArrayList<String> violations = new ArrayList<>();
	private List<String> srcList = new ArrayList<>();
	private int violationCount = 0;
	private int checkCompile ;
	private String mainPath = "";
	
	
	public String start(String workpath, String policy)
	{
		EvaluationSchemeMapper scheme = new EvaluationSchemeMapper();
		JsonObject policyObject = new Gson().fromJson(policy, JsonObject.class);
		JsonObject score = new JsonObject();
		EntireContentParser source = new EntireContentParser();
		MainClassDetector mcd = new MainClassDetector();
		
		new PolicyParser().parse(scheme, policyObject);
		
		score.addProperty("token", scheme.getToken());
		score.addProperty("isDirect", scheme.isDirect()) ;
		score.addProperty("className", scheme.getClassName());
		score.addProperty("instructor", scheme.getInstructor());
		score.addProperty("point", scheme.getPoint());
		
		flag = scheme.isBTool() ? true : false;
		IGradeStage grader = scheme.isBTool() ? new GradleStage() : new JavaStage();
		
		checkCompile = grader.compile(workpath);

		srcList = source.getAllFiles(workpath);

		/*
		if(!scheme.isBTool())
		{
			// mainPath = isExistMainPath(scheme);
			
			if (mainPath == null || mainPath.equals(""))
			{
				mcd.setFilePath(workpath);
				
				for (String src : srcList) 
				{
					if (!(mainPath = mcd.find(src)).isBlank()) 
					{
						mainPath.trim();
						break;
					}
				}
			}
		}
		*/
		
		if(!scheme.isBTool()) mainPath = scheme.getReqClass().get(0);
		
		if (checkCompile == 0) 
		{	
			JsonObject item = new JsonObject();
			item.addProperty("violation", false);
			item.addProperty("deductedPoint", 0);
			
			score.add("compile", item);
			
			if (scheme.isTest()) // 테스트 케이스 있는 경우 
			{
				for (int i = 0; i < scheme.getInputs().size(); i++) 
				{
					boolean result = false;
					
					if (!flag) 
					{
						result = grader.build(
								grader.getTest(mainPath, scheme.getInputs().get(i), scheme.isTest()),
								scheme.getOutputs().get(i),
								workpath);
					} 
					else 
					{	
						result = grader.build(
								grader.getTest(scheme.getInputs().get(i), scheme.isTest()), 
								scheme.getOutputs().get(i), 
								workpath);
					}
						
					if (!result) 
					{
						violations.add(String.valueOf(i + 1));
						violationCount++;
					}
				}
				
				JsonObject item_class = new JsonObject();
				
				if (violationCount > 0)
					item_class.addProperty("violation", true);
				else
					item_class.addProperty("violation", false);
				
				item_class.add("violationNumber", new Gson().toJsonTree(violations));
				item_class.addProperty("violationCount", violationCount);
				
				double deducted = (double)(scheme.getRuntime_deduct_point() * (double)violationCount);
				if (deducted > scheme.getRuntime_max_deduct())
					deducted = scheme.getRuntime_max_deduct();
				
				scheme.deduct_point(deducted);
				item_class.addProperty("deductedPoint", deducted);
				score.add("oracle", item_class);
			}	
		} 
		else 
		{
			if (scheme.isTest())
			{
				for (int i = 0; i < scheme.getInputs().size(); i++) violations.add(String.valueOf(i + 1));
				
				JsonObject item_class = new JsonObject();
				
				if(scheme.getInputs().size() > 0)
					item_class.addProperty("violation", true);
				else
					item_class.addProperty("violation", false);
				
				item_class.add("violationNumber", new Gson().toJsonTree(violations));
				item_class.addProperty("violationCount", scheme.getInputs().size());
				
				double deducted = scheme.getRuntime_max_deduct();
				
				scheme.deduct_point(deducted);
				item_class.addProperty("deductedPoint", deducted);
				score.add("oracle", item_class);
			}	

			JsonObject item = new JsonObject();
			item.addProperty("violation", true);
			item.addProperty("deductedPoint", scheme.getCompiled_deduct_point());
			
			score.add("compile", item);
			
			scheme.deduct_point(scheme.getCompiled_deduct_point());
		}

		new OOPChecker(scheme, srcList, "", workpath).run(score, scheme.isBTool());
		new ImplementationChecker(scheme, srcList, "", workpath).run(score, scheme.isBTool());
		
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		try {
			Date dueDate = format.parse(scheme.getDueDate());
			Date nowDate = new Date();
			
			int compare = nowDate.compareTo(dueDate);
			double calDate = Math.abs(nowDate.getTime() - dueDate.getTime());
			
			double calDateDays = Math.ceil(calDate / (double)(24*60*60*1000)); 

			if(compare > 0)
			{
				System.out.println("delay : "  + calDateDays);
				
				JsonObject item = new JsonObject();
				item.addProperty("violation", true);
				item.addProperty("deductedPoint", calDateDays);
				score.add("delay", item);
				
				scheme.deduct_point(calDateDays);
			}
			else
			{
				JsonObject item = new JsonObject();
				item.addProperty("violation", false);
				item.addProperty("deductedPoint", 0);
				score.add("delay", item);
			}
		} catch (ParseException e) { e.printStackTrace(); }
		
		if((double)scheme.getResult_point() > 0)
			score.addProperty("result", (double)scheme.getResult_point());
		else
			score.addProperty("result", 0);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String sheet = gson.toJson(score);
		
		return sheet;
	}
}
