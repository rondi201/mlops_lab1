pipeline {
    agent any

    environment {
        DOCKERHUB_CREDS=credentials('mlops_lab')
        PROJECT_NAME='mlops_lab1'
    }

options {
        timestamps()
        skipDefaultCheckout(true)
	}
    stages {
        stage('Checkout repo dir') {
            steps {
                    sh 'git clone https://github.com/rondi201/${PROJECT_NAME}.git'
                    sh 'cd ${PROJECT_NAME} && ls -lash'
                    sh 'whoami'
				}
			}

        stage('Login'){
            steps{
                    sh 'docker login -u ${DOCKERHUB_CREDS_USR} -p ${DOCKERHUB_CREDS_PSW}'
                }
            }

        stage('Create and run docker container') {
            steps {
                script {
                    try {
                            sh 'cd ${PROJECT_NAME} && docker compose build'
                        }

                    finally {
                            sh 'cd ${PROJECT_NAME} && docker compose up -d'
                        }
				    }
                }
            }

        stage('Check container healthy') {
            options {
                timeout(time: 180, unit: 'SECONDS')
            }
            steps {
                dir("${PROJECT_NAME}") {
                    sh '''
                        containerId=$(docker ps -qf "name=^${PROJECT_NAME}-api")
                        until [ "`docker inspect -f {{.State.Health.Status}} $containerId`"=="healthy" ]; do
                            sleep 0.1;
                        done;
                    '''
                }
            }
        }

        stage('Checkout container logs') {
            steps {
                dir("${PROJECT_NAME}") {
                    script {
                        try {
                            timeout(time: 30, unit: 'SECONDS') {
                                sh '''
                                    containerId=$(docker ps -qf "name=^${PROJECT_NAME}-api")
                                    if [[ -z "$containerId" ]]; then
                                        echo "No container running"
                                    else
                                        docker logs --tail 100 -f "$containerId"
                                    fi
                                '''
                            }
                        }
                        catch (err) {}
                    }
                }
            }
        }

        stage('Checkout coverage report'){
            steps{
                dir("${PROJECT_NAME}"){
                        sh '''
                        docker compose logs -t --tail 10
                        '''
                }
            }
        }

        stage('Push'){
            steps{
                    sh 'docker push ${DOCKERHUB_CREDS_USR}/${PROJECT_NAME}-api:latest'
            }
        }
	}

    post {
        always {
                sh 'docker logout'
                script {
                    if (fileExists("${PROJECT_NAME}/docker-compose.yaml")) {
                        sh 'cd ${PROJECT_NAME} && docker compose down -v'
                    }
                }
                cleanWs()
        }
    }
}