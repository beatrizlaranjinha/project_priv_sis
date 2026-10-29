features:
- contextual integrity using xacml access control
- gdpr consent management, data access and erasure
- k-anonymity using the mondrian algorithm
- t-closeness using earth mover's distance
- differential privacy with laplace noise and privacy budget management
- linear regression experiments comparing privacy and data utility

requirements:
- java
- maven
- sqlite

how to run:
open a terminal in the project directory containing `pom.xml`.
run all tests:
`mvn clean test`
compile and launch the application:
`mvn clean compile exec:java`


notes: the application uses a local sqlite database. differential privacy results contain random noise, so experimental results may vary between executions.
