package fr.inria.astor.core.loop.evolutionary.population;

import java.util.List;

import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.ProgramVariantValidationResult;
/**
 * Population Controller: it selects the program variants for the following generations.
 * @author Matias Martinez,  matias.martinez@inria.fr
 *
 */
public interface PopulationController {

	/**
	 * 
	 * @param parentVariants Originals variant 
	 * @param childVariants New Variants
	 * @param maxNumberInstances
	 * @return 
	 */
	public List<ProgramVariant> selectProgramVariantsForNextGeneration(List<ProgramVariant> parentVariants,
			List<ProgramVariant> childVariants,List<ProgramVariant> solutions,int maxNumberInstances);


	public double getFitnessValue(ProgramVariant variant ,ProgramVariantValidationResult valResult );
}
