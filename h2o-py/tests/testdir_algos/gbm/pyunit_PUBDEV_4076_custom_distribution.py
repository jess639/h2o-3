import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.distributions import CustomDistributionGaussian, CustomDistributionBernoulli, \
    CustomDistributionMultinomial, CustomDistributionGeneric
from tests.pyunit_utils import regression_model_distribution, regression_model_default
from tests.pyunit_utils import multinomial_model_default, multinomial_model_distribution
from tests.pyunit_utils import binomial_model_default, binomial_model_distribution, check_model_metrics

from h2o.estimators.gbm import H2OGradientBoostingEstimator


class CustomDistributionGaussianWrong(CustomDistributionGaussian):

    def gradient(self, y, f):
        return (y - f) * (y - f)


class CustomDistributionGaussianNoInh(object):

    def link(self):
        return "identity"

    def init(self, w, o, y):
        return [w * (y - o), w]

    def gradient(self, y, f):
        return y - f

    def gamma(self, w, y, z, f):
        return [w * z, w]
    

def upload_distribution(distribution, name):
    return h2o.upload_custom_distribution(distribution, func_name="custom_"+name,
                                          func_file="custom_"+name+".py")


# Test a custom distributions are computed correctly
def test_custom_distribution_computation():
    test_regression()
    test_binomial()
    test_multinomial()
    test_null()
    test_inherited_custom_distribution()
    
    
def test_regression():
    print("Regression tests")
    name = "gaussian"
    print("Create default", name,  "model")
    (model, f_test) = regression_model_default(H2OGradientBoostingEstimator, name)
    print("Create custom ", name,  "model")
    (model2, f_test2) = regression_model_distribution(H2OGradientBoostingEstimator, 
                                                      upload_distribution(CustomDistributionGaussian, name))
    check_model_metrics(model, model2, name)
    
    
def test_binomial():
    print("Binomial tests")
    name = "Bernoulli"
    print("Create default", name,  "model")
    (model, f_test) = binomial_model_default(H2OGradientBoostingEstimator, name)
    print("Create custom ", name,  "model")
    (model2, f_test2) = binomial_model_distribution(H2OGradientBoostingEstimator, 
                                                    upload_distribution(CustomDistributionBernoulli, name))
    check_model_metrics(model, model2, name)
        

def test_multinomial():
    print("Multinomial test")
    name = "multinomial"
    print("Create default", name,  "model")
    (model, f_test) = multinomial_model_default(H2OGradientBoostingEstimator)
    print("Create custom", name, "model")
    (model2, f_test2) = multinomial_model_distribution(H2OGradientBoostingEstimator, 
                                                       upload_distribution(CustomDistributionMultinomial, name))
    check_model_metrics(model, model2, name)
                 

def test_null():
    print("Null model test")
    print("Create custom null model")
    (model, f_test) = regression_model_distribution(H2OGradientBoostingEstimator,
                                                    upload_distribution(CustomDistributionGeneric, "null"))
    print("rmse null model train:", model.rmse(valid=False))
    print("rmse null model valid:", model.rmse(valid=True))
    
    
# Test custom distribution inheritance    
def test_inherited_custom_distribution():
    print("Create default gaussian model")
    (model, f_test) = regression_model_default(H2OGradientBoostingEstimator, "gaussian")
    
    print("Create custom wrong gaussian model")
    (model2, f_test2) = regression_model_distribution(H2OGradientBoostingEstimator, 
                                                      upload_distribution(CustomDistributionGaussianWrong, "gaussian_w"))
    assert model.rmse(valid=False) != model2.rmse(valid=False), \
        "Training rmse is not different for default and custom gaussian model."
    assert model.rmse(valid=True) != model2.rmse(valid=True), \
        "Validation rmse is not different for default and custom gaussian model."
    
    print("Create custom gaussian model without inheritance")
    (model3, f_test3) = regression_model_distribution(H2OGradientBoostingEstimator,
                                                      upload_distribution(CustomDistributionGaussianNoInh, "gaussian_ni"))
    check_model_metrics(model, model3, "gaussian_ni")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_custom_distribution_computation)
else:
    test_custom_distribution_computation()

