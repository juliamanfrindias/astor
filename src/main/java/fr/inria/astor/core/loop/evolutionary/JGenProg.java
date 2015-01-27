package fr.inria.astor.core.loop.evolutionary;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;

import com.martiansoftware.jsap.JSAPException;

import fr.inria.astor.core.entities.Gen;
import fr.inria.astor.core.entities.GenOperationInstance;
import fr.inria.astor.core.entities.GenSuspicious;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.taxonomy.GenProgMutationOperation;
import fr.inria.astor.core.faultlocalization.entity.SuspiciousCode;
import fr.inria.astor.core.loop.evolutionary.spaces.implementation.spoon.processor.AbstractFixSpaceProcessor;
import fr.inria.astor.core.loop.evolutionary.transformators.CtExpressionTransformator;
import fr.inria.astor.core.loop.evolutionary.transformators.CtStatementTransformator;
import fr.inria.astor.core.loop.evolutionary.transformators.ModelTransformator;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.TransformationProperties;
import fr.inria.astor.core.setup.ProjectRepairFacade;

/**
 * Extension of Evolutionary loop with GenProgOperations
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class JGenProg extends EvolutionaryEngine {

	
	Map<String, List<String>> appliedCache = new HashMap<String, List<String>>();


	public JGenProg(MutationSupporter mutatorExecutor, ProjectRepairFacade projFacade) throws JSAPException {
		super(mutatorExecutor, projFacade);
	}

	/**
	 * By default, init the spoon model. It should not be created before.
	 * Otherwise, an exception occurs.
	 * 
	 * @param suspicious
	 * @throws Exception
	 */
	public void start(List<SuspiciousCode> suspicious) throws Exception {
		this.start(suspicious, true);
	}

	/**
	 * 
	 * @param suspicious
	 * @throws Exception
	 */
	public void start(List<SuspiciousCode> suspicious, boolean buildSpoonModel) throws Exception {

		if (buildSpoonModel && !(this.mutatorSupporter.getFactory().Type().getAll().size()>0)) {
			initModel();
		}

		log.debug("----Starting Mutation: Initial suspicious size: " + suspicious.size());
		long startT = System.currentTimeMillis();

		initializePopulation(suspicious);
		
		if(originalVariant.getGenList().isEmpty()){
			log.debug("No gen to analyze");
			return;
		}
				
		URL[] originalURL = projectFacade.getURLforMutation(ProgramVariant.DEFAULT_ORIGINAL_VARIANT);
		URLClassLoader loader = new URLClassLoader(originalURL);
		Thread.currentThread().setContextClassLoader(loader);
		
		boolean validInstance = validateInstance(originalVariant);
		assert (validInstance);
		
		for (ProgramVariant initvariant : variants) {
			initvariant.setFitness(originalVariant.getFitness());
		}
	
		startEvolution();

		long endT = System.currentTimeMillis();
		log.debug("Time (ms): " + (endT - startT));
		currentStat.timeIteraction = ((endT-startT));
		
	}

	public void initModel() {
		String codeLocation = projectFacade.getInDirWithPrefix(ProgramVariant.DEFAULT_ORIGINAL_VARIANT);
		String classpath = projectFacade.getProperties().getDependenciesString();
		mutatorSupporter.buildModel(codeLocation,classpath);
	}

	public void initializePopulation(List<SuspiciousCode> suspicious) throws Exception {
		variantFactory.setMutatorExecutor(getMutatorSupporter());
		variantFactory.setFixspace(getFixspace());
		this.variants = variantFactory.createInitialPopulation(suspicious, TransformationProperties.populationSize,
				populationControler, projectFacade);

		// We save the first variant
		this.originalVariant = variants.get(0);
		currentStat.fl_gens_size = this.originalVariant.getGenList().size();
	}

	/**
	 * This method updates gens of a variant according to a created GenOperationInstance
	 * @param variant variant to modify the gen information
	 * @param operationofGen operator to apply in the variant.
	 */
	@Override
	protected void updateVariantGenList(ProgramVariant variant, GenOperationInstance operation) {
		List<Gen> gens = variant.getGenList();
		GenProgMutationOperation type = (GenProgMutationOperation) operation.getOperationApplied();
		if (type.equals(GenProgMutationOperation.DELETE) || type.equals(GenProgMutationOperation.REPLACE)) {
			boolean removed = gens.remove(operation.getGen());
			log.debug("---updating gen list " + operation + " removed gen? " + removed);
		}
		if (!type.equals(GenProgMutationOperation.DELETE)) {
			Gen existingGen = operation.getGen();
			Gen newGen = null;
			if (existingGen instanceof GenSuspicious)
				newGen = variantFactory.cloneGen((GenSuspicious) existingGen, operation.getModified());
			else
				newGen = variantFactory.cloneGen(existingGen, operation.getModified());

			log.debug("---updating gen list " + operation + " adding gen: " + newGen);
			gens.add(newGen);
		}

	}

	/**
	 * Create a Gen Mutation for a given CtElement
	 * 
	 * @param ctElementPointed
	 * @param className
	 * @param suspValue
	 * @return
	 * @throws IllegalAccessException
	 */
	@Override
	protected GenOperationInstance createGenMutationForElement(Gen gen) throws IllegalAccessException {
		GenSuspicious genSusp = (GenSuspicious) gen;

		GenProgMutationOperation operationType = (GenProgMutationOperation) repairSpace.getNextMutation(genSusp
				.getSuspicious().getSuspiciousValue());

		if (operationType == null) {
			return null;
		}

		CtElement targetStmt = genSusp.getRootElement();
		CtElement cparent = targetStmt.getParent();
		
		GenOperationInstance operation = new GenOperationInstance();
		operation.setOriginal(targetStmt);
		operation.setOperationApplied(operationType);
		operation.setGen(genSusp);
		
		if ((cparent != null && (cparent instanceof CtBlock))) {
			CtBlock parentBlock = (CtBlock) cparent;
			operation.setParentBlock(parentBlock);
		}
		
		CtElement fix = null;
		if (operationType.equals(GenProgMutationOperation.INSERT_AFTER)
				|| operationType.equals(GenProgMutationOperation.INSERT_BEFORE)
				|| (operationType.equals(GenProgMutationOperation.REPLACE))
					) {
		//	fix = this.fixspace.getElementFromSpace(gen.getCtClass().getQualifiedName());
			//}else
		//	if (operationType.equals(GenProgMutationOperation.REPLACE)) {
			fix = this.fixspace.getElementFromSpace(gen.getCtClass().getQualifiedName(),gen.getRootElement().getClass().getName());
			
			if(fix == null){
				System.err.println("fix ingredient null");
			}	
		}
		
		
		operation.setModified(fix);

		return operation;
	}
	/**
	 * Return fix ingredient considering cache.
	 * @param gen
	 * @param targetStmt
	 * @param elementsFromFixSpace
	 * @return
	 */
	protected CtElement getFixIngredient(Gen gen, CtElement targetStmt, int elementsFromFixSpace) {
		CtElement fix = null;
		int attempts = 0;
		boolean continueSearching = true;
		while(continueSearching && attempts < elementsFromFixSpace){
			fix = this.fixspace.getElementFromSpace(gen.getCtClass().getQualifiedName());
			if(fix == null){
				return null;
			}
			attempts++;
			if (fix.getSignature().equals(targetStmt.getSignature()) ) {
				log.debug("Discarting operation, replacement is the same than the replaced code");
				// Discard the operation.
			}else
				continueSearching = alreadyApplied(gen.getProgramVariant().getId(),fix.toString(), targetStmt.toString());
		
		} 
		if(continueSearching ){
			log.debug("All mutations were applied: no mutation left to apply");
			return null;
		}
		return fix;
	}

	public void undoOperationToSpoonElement(GenOperationInstance operation) {
		List<AbstractFixSpaceProcessor> processors = this.getVariantFactory().getProcessors();
		for (AbstractFixSpaceProcessor processor : processors) {
			ModelTransformator mt = processor.getTransformator();
			if(mt.canTransform(operation)){
				try {
					mt.revert(operation);
				} catch (Exception e) {
					e.printStackTrace();
			}
			//After the operator instance is processed by one transformator, we break.
			break;
			}
		}

	}

	/**
	 * 
	 */
	@Override
	protected void applyPreviousMutationOperationToSpoonElement(GenOperationInstance operation)
			throws IllegalAccessException {
		this.applyNewMutationOperationToSpoonElement(operation);

	}

	/**
	 * Apply a given Mutation to the node referenced by the operation
	 * 
	 * @param operation
	 * @throws IllegalAccessException
	 */
	CtExpressionTransformator expTransform = new CtExpressionTransformator();
	CtStatementTransformator stTransformator = new CtStatementTransformator();
	protected void applyNewMutationOperationToSpoonElement(GenOperationInstance operation)
			throws IllegalAccessException {
				
		List<AbstractFixSpaceProcessor> processors = this.getVariantFactory().getProcessors();
		for (AbstractFixSpaceProcessor processor : processors) {
			ModelTransformator mt = processor.getTransformator();
			if(mt.canTransform(operation)){
				try {
					mt.transform(operation);
				} catch (Exception e) {
					e.printStackTrace();
			}
				//After the operator instance is processed by one transformator, we break.
				break;
				
			}
		}
		

	}

	public boolean isSuccessApplied(String previous, CtStatement sts) {
		CtBlock parent = (CtBlock) sts.getParent();
		return !parent.toString().equals(previous);

	}

	/**
	 * Check if the fix were applied in the location for a program instance
	 * @param id program instance id.
	 * @param fix
	 * @param location
	 * @return
	 */
	public boolean alreadyApplied(int id, String fix, String location){
		//we add the instance identifier to the patch.
		String lockey = location;//+"-"+id;
		List<String> prev = appliedCache.get(lockey);
		//The element does not have any mutation applied
		if(prev == null){
			prev = new ArrayList<String>();
			prev.add(fix);
			appliedCache.put(lockey,prev);
			return false;
		}else{
			//The element has mutation applied
			if(prev.contains(fix))
				return true;
			else{
				prev.add(fix);
				return false;
			}
		}
	}
	
}
