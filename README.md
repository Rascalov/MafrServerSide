# MAFR Tomcat Server

A basic Java Servlet that uses tomcat which the client can communicate with.

Be sure to enter your Moodle credentials in the `authConfig.xml` file. 
You can set a server password if you want the server to remain somewhat private. 

You also have the option to turn auto-enrollment to `true` or `false`. It's on `true` by default.

### Deployment
I only ever used this implementation on heroku. You can set up a free server with it and it's easy to deploy and update. 

So set up a heroku account.

Get the Heroku CLI.

Set up git (since you clone this file, it should already be set up)

`heroku create --region eu `

`git push heroku master`

<a href="https://devcenter.heroku.com/articles/create-a-java-web-application-using-embedded-tomcat#deploy-your-application-to-heroku"> 
Click this link for more info on deploying, make sure you set the region to EU.
</a>

And you're done, go to the created site with `heroku open` to check if it's online. 

The server takes  ~10 seconds to do the initial login. After that, it will respond to client requests.
